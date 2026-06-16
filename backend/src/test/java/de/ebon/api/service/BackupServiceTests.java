package de.ebon.api.service;

import de.ebon.api.dto.BackupRestoreResultDto;
import de.ebon.api.dto.BackupValidationReportDto;
import de.ebon.backup.BackupRestoreLock;
import de.ebon.backup.BackupRestoreLockedException;
import de.ebon.backup.BackupRestoreWriteGuardFilter;
import de.ebon.support.PostgresIntegrationTestSupport;
import de.ebon.system.VersionService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "app.sync.scheduler.enabled=false")
class BackupServiceTests extends PostgresIntegrationTestSupport {

    private static final String TOKEN_KEY = "paperless_api_token";

    @Autowired
    private BackupService backupService;

    @Autowired
    private BackupRestoreLock backupRestoreLock;

    @Autowired
    private BackupRestoreWriteGuardFilter writeGuardFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VersionService versionService;

    private byte[] baselineBackup;

    @BeforeEach
    void captureBaseline() {
        baselineBackup = backupService.createBackup().content();
    }

    @AfterEach
    void restoreBaseline() {
        if (baselineBackup != null) {
            backupService.restore(multipart(baselineBackup));
        }
    }

    // Verifies backups mask stored secrets and validation is a dry-run that does not mutate database data.
    @Test
    void createBackupMasksSecretsAndValidateKeepsDatabaseUnchanged() throws Exception {
        upsertSetting(TOKEN_KEY, "plain-paperless-secret");
        long categoriesBefore = countRows("category");

        BackupService.BackupFile backupFile = backupService.createBackup();
        String settingsJson = readZipEntry(backupFile.content(), "app_settings.json");
        BackupValidationReportDto validation = backupService.validate(multipart(backupFile.content()));

        assertThat(backupFile.filename()).startsWith("ebon-backup-").endsWith(".zip");
        assertThat(settingsJson).doesNotContain("plain-paperless-secret");
        assertThat(settingsJson).contains("\"key\":\"paperless_api_token\"");
        assertThat(settingsJson).contains("\"requiresReconfiguration\":true");
        assertThat(validation.valid()).isTrue();
        assertThat(validation.tables())
                .anySatisfy(table -> assertThat(table.name()).isEqualTo("categories"));
        assertThat(countRows("category")).isEqualTo(categoriesBefore);
    }

    // Verifies automatic backups use the same ZIP content and secret masking while getting an auto-specific filename.
    @Test
    void createAutomaticBackupUsesSameMaskedManifestFormat() throws Exception {
        upsertSetting(TOKEN_KEY, "plain-paperless-secret");

        BackupService.BackupFile backupFile = backupService.createAutomaticBackup();
        String manifestJson = readZipEntry(backupFile.content(), "manifest.json");
        String settingsJson = readZipEntry(backupFile.content(), "app_settings.json");

        assertThat(backupFile.filename()).startsWith("ebon-backup-auto-").endsWith(".zip");
        assertThat(manifestJson).contains("\"appVersion\":\"" + versionService.version() + "\"");
        assertThat(settingsJson).doesNotContain("plain-paperless-secret");
        assertThat(settingsJson).contains("\"requiresReconfiguration\":true");
    }

    // Verifies restore fully replaces mutable application data and requires masked secrets to be configured again.
    @Test
    void restoreReplacesDataAndLeavesMaskedSecretsEmpty() {
        Long categoryId = upsertCategory("Backup Restore Kategorie");
        seedReceipt(91001, categoryId, "Restore Test Position");
        upsertSetting(TOKEN_KEY, "secret-before-backup");
        byte[] backupContent = backupService.createBackup().content();

        upsertCategory("Kategorie nach Backup");
        jdbcTemplate.update("delete from receipt where paperless_document_id = ?", 91001);
        upsertSetting(TOKEN_KEY, "changed-after-backup");

        BackupRestoreResultDto result = backupService.restore(multipart(backupContent));

        assertThat(result.validation().valid()).isTrue();
        assertThat(result.message()).isEqualTo("Backup wurde wiederhergestellt.");
        assertThat(countRowsWhere("category", "name = 'Backup Restore Kategorie'")).isEqualTo(1);
        assertThat(countRowsWhere("category", "name = 'Kategorie nach Backup'")).isZero();
        assertThat(countRowsWhere("receipt", "paperless_document_id = 91001")).isEqualTo(1);
        assertThat(settingValue(TOKEN_KEY)).isEmpty();
    }

    // Verifies a failed restore rolls back all deletes and inserts instead of leaving a partially restored database.
    @Test
    void restoreRollsBackWhenBackupRowsViolateConstraints() throws Exception {
        Long categoryId = upsertCategory("Rollback Kategorie");
        seedReceipt(91002, categoryId, "Rollback Test Position");
        byte[] backupContent = backupService.createBackup().content();
        byte[] corruptBackup = replaceZipEntryText(
                backupContent,
                "receipt_items.json",
                "\"total_price\":\"1.23\"",
                "\"total_price\":\"not-a-number\"");
        upsertCategory("Rollback Marker");

        assertThatThrownBy(() -> backupService.restore(multipart(corruptBackup)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        assertThat(countRowsWhere("category", "name = 'Rollback Marker'")).isEqualTo(1);
        assertThat(countRowsWhere("receipt", "paperless_document_id = 91002")).isEqualTo(1);
    }

    // Verifies dry-run catches broken internal references before a destructive restore is attempted.
    @Test
    void validateRejectsBrokenForeignKeyReferences() throws Exception {
        Long categoryId = upsertCategory("Broken Reference Kategorie");
        Long receiptId = seedReceipt(91003, categoryId, "Broken Reference Position");
        byte[] backupContent = backupService.createBackup().content();
        byte[] corruptBackup = replaceZipEntryText(
                backupContent,
                "receipt_items.json",
                "\"receipt_id\":\"" + receiptId + "\"",
                "\"receipt_id\":\"999999999\"");

        BackupValidationReportDto validation = backupService.validate(multipart(corruptBackup));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors())
                .contains("Referenz receipt_items.receipt_id=999999999 zeigt auf keinen Backup-Datensatz.");
    }

    // Verifies the dry-run rejects incompatible backup manifests before restore can modify data.
    @Test
    void validateRejectsIncompatibleManifestVersion() throws Exception {
        byte[] backupContent = backupService.createBackup().content();
        byte[] incompatibleBackup = replaceZipEntryText(
                backupContent,
                "manifest.json",
                "\"version\":\"1\"",
                "\"version\":\"999\"");

        BackupValidationReportDto validation = backupService.validate(multipart(incompatibleBackup));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.manifestVersion()).isEqualTo("999");
        assertThat(validation.errors()).contains("Manifest-Version 999 ist nicht kompatibel.");
    }

    // Verifies backup restore cannot persist arbitrary category icon values outside the backend allowlist.
    @Test
    void validateRejectsUnknownCategoryIconValues() throws Exception {
        byte[] backupContent = backupService.createBackup().content();
        byte[] invalidIconBackup = replaceZipEntryText(
                backupContent,
                "categories.json",
                "\"icon\":\"shopping-basket\"",
                "\"icon\":\"<svg/onload=alert(1)>\"");

        BackupValidationReportDto validation = backupService.validate(multipart(invalidIconBackup));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors())
                .anySatisfy(error -> assertThat(error)
                        .contains("Kategorie-Icon <svg/onload=alert(1)> ist nicht erlaubt."));
    }

    // Verifies nested backup/restore operations are rejected so two destructive operations cannot overlap.
    @Test
    void backupRestoreLockRejectsNestedOperations() {
        assertThatThrownBy(() -> backupRestoreLock.runLocked(
                "OUTER",
                () -> backupRestoreLock.runLocked("INNER", () -> "not reached")))
                .isInstanceOf(BackupRestoreLockedException.class)
                .hasMessage("Backup oder Restore laeuft bereits.");
    }

    // Verifies mutating API requests are blocked while backup/restore holds the write lock.
    @Test
    void writeGuardBlocksMutatingRequestsWhileBackupRestoreIsLocked() {
        backupRestoreLock.runLocked("RESTORE", () -> {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/receipts/1/reparse");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean filterChainReached = new AtomicBoolean(false);

            try {
                writeGuardFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                        filterChainReached.set(true));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }

            assertThat(response.getStatus()).isEqualTo(423);
            assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                    .contains("Backup/Restore laeuft");
            assertThat(filterChainReached).isFalse();
            return null;
        });
    }

    private Long seedReceipt(int paperlessDocumentId, Long categoryId, String description) {
        Long receiptId = jdbcTemplate.queryForObject("""
                insert into receipt (paperless_document_id, receipt_date, store_name, total_amount, raw_text, parse_status)
                values (?, date '2026-06-01', 'REWE', 1.23, 'raw receipt text', 'PARSED')
                returning id
                """, Long.class, paperlessDocumentId);
        Long itemId = jdbcTemplate.queryForObject("""
                insert into receipt_item
                    (receipt_id, position_index, description, total_price, category_id, category_source)
                values (?, 0, ?, 1.23, ?, 'RULE')
                returning id
                """, Long.class, receiptId, description, categoryId);
        jdbcTemplate.update("""
                insert into ai_categorization_log
                    (receipt_item_id, prompt_sent, response_received, assigned_category_id, ai_confidence, model_used)
                values (?, 'prompt', 'response', ?, 0.950, 'mock-model')
                """, itemId, categoryId);
        return receiptId;
    }

    private Long upsertCategory(String name) {
        return jdbcTemplate.queryForObject("""
                insert into category (name, color_hex, icon, is_active, sort_order)
                values (?, '#123456', 'tag', true, 999)
                on conflict (name) do update set color_hex = excluded.color_hex
                returning id
                """, Long.class, name);
    }

    private void upsertSetting(String key, String value) {
        jdbcTemplate.update("""
                insert into app_settings (key, value, description)
                values (?, ?, 'test setting')
                on conflict (key) do update set value = excluded.value
                """, key, value);
    }

    private String settingValue(String key) {
        return jdbcTemplate.queryForObject(
                "select value from app_settings where key = ?",
                String.class,
                key);
    }

    private long countRows(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private long countRowsWhere(String table, String whereClause) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + whereClause, Long.class);
    }

    private static MultipartFile multipart(byte[] content) {
        return new MockMultipartFile("file", "backup.zip", "application/zip", content);
    }

    private static String readZipEntry(byte[] content, String entryName) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalArgumentException("ZIP entry not found: " + entryName);
    }

    private static byte[] replaceZipEntryText(
            byte[] content,
            String entryName,
            String expectedText,
            String replacementText) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] entryContent = zip.readAllBytes();
                if (entryName.equals(entry.getName())) {
                    String text = new String(entryContent, StandardCharsets.UTF_8);
                    assertThat(text).contains(expectedText);
                    entryContent = text.replace(expectedText, replacementText).getBytes(StandardCharsets.UTF_8);
                }
                entries.put(entry.getName(), entryContent);
            }
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }
}
