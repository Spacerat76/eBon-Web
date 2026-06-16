package de.ebon.api.service;

import de.ebon.api.dto.BackupRestoreResultDto;
import de.ebon.api.dto.BackupTableValidationDto;
import de.ebon.api.dto.BackupValidationReportDto;
import de.ebon.backup.BackupRestoreLock;
import de.ebon.categorization.CategoryIconRegistry;
import de.ebon.system.VersionService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class BackupService {

    private static final String MANIFEST_VERSION = "1";
    private static final Set<String> SECRET_SETTING_KEYS = Set.of("paperless_api_token", "openrouter_api_key");
    private static final TypeReference<Map<String, Object>> MANIFEST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> ROW_LIST_TYPE = new TypeReference<>() {
    };
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AUTOMATIC_FILE_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
            .withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final BackupRestoreLock backupRestoreLock;
    private final CategoryIconRegistry categoryIconRegistry;
    private final TransactionTemplate transactionTemplate;
    private final VersionService versionService;
    private final List<BackupTable> tables = backupTables();

    public BackupService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            BackupRestoreLock backupRestoreLock,
            CategoryIconRegistry categoryIconRegistry,
            TransactionTemplate transactionTemplate,
            VersionService versionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.backupRestoreLock = backupRestoreLock;
        this.categoryIconRegistry = categoryIconRegistry;
        this.transactionTemplate = transactionTemplate;
        this.versionService = versionService;
    }

    public BackupFile createBackup() {
        return backupRestoreLock.runLocked("BACKUP", () -> createBackupUnlocked("ebon-backup-", FILE_TIMESTAMP_FORMATTER));
    }

    public BackupFile createAutomaticBackup() {
        return backupRestoreLock.runLocked(
                "AUTOMATIC_BACKUP",
                () -> createBackupUnlocked("ebon-backup-auto-", AUTOMATIC_FILE_TIMESTAMP_FORMATTER));
    }

    public BackupValidationReportDto validate(MultipartFile file) {
        return readArchive(file).report();
    }

    public BackupRestoreResultDto restore(MultipartFile file) {
        return backupRestoreLock.runLocked("RESTORE", () -> {
            BackupArchive archive = readArchive(file);
            if (!archive.report().valid()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "Backup ist ungueltig: " + String.join("; ", archive.report().errors()));
            }

            transactionTemplate.executeWithoutResult(status -> restoreArchive(archive));
            return new BackupRestoreResultDto("Backup wurde wiederhergestellt.", archive.report());
        });
    }

    private BackupFile createBackupUnlocked(String filenamePrefix, DateTimeFormatter filenameFormatter) {
        Instant now = clock.instant();
        Map<String, Long> recordCounts = new LinkedHashMap<>();
        byte[] content;

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            Map<String, List<Map<String, Object>>> tableRows = new LinkedHashMap<>();
            for (BackupTable table : tables) {
                List<Map<String, Object>> rows = exportRows(table);
                tableRows.put(table.logicalName(), rows);
                recordCounts.put(table.logicalName(), (long) rows.size());
            }

            writeZipEntry(zip, "manifest.json", manifest(now, recordCounts));
            for (BackupTable table : tables) {
                writeZipEntry(zip, table.fileName(), tableRows.get(table.logicalName()));
            }
            zip.finish();
            content = output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return new BackupFile(filenamePrefix + filenameFormatter.format(now) + ".zip", content);
    }

    private List<Map<String, Object>> exportRows(BackupTable table) {
        String selectColumns = table.columns().stream()
                .map(column -> column.name() + "::text AS " + column.name())
                .reduce((first, second) -> first + ", " + second)
                .orElseThrow();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + selectColumns + " FROM " + table.tableName() + " ORDER BY " + table.orderBy());

        if (!"app_settings".equals(table.logicalName())) {
            return rows;
        }

        return rows.stream()
                .map(this::maskAppSettingSecret)
                .toList();
    }

    private Map<String, Object> maskAppSettingSecret(Map<String, Object> row) {
        Map<String, Object> masked = new LinkedHashMap<>(row);
        Object key = masked.get("key");
        if (key != null && SECRET_SETTING_KEYS.contains(key.toString())) {
            masked.put("value", null);
            masked.put("requiresReconfiguration", true);
        }
        return masked;
    }

    private Map<String, Object> manifest(Instant createdAt, Map<String, Long> recordCounts) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", MANIFEST_VERSION);
        manifest.put("appVersion", versionService.version());
        manifest.put("createdAt", createdAt.toString());
        manifest.put("tables", tables.stream().map(BackupTable::logicalName).toList());
        manifest.put("recordCounts", recordCounts);
        return manifest;
    }

    private void writeZipEntry(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(objectMapper.writeValueAsBytes(value));
        zip.closeEntry();
    }

    private BackupArchive readArchive(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return invalidArchive(null, "Backup-Datei fehlt oder ist leer.");
        }

        Map<String, byte[]> entries;
        try {
            entries = readZipEntries(file.getBytes());
        } catch (IOException exception) {
            return invalidArchive(null, "Backup-ZIP konnte nicht gelesen werden.");
        }

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Object> manifest = readManifest(entries, errors);
        String manifestVersion = text(manifest, "version");
        if (manifest != null && !MANIFEST_VERSION.equals(manifestVersion)) {
            errors.add("Manifest-Version " + manifestVersion + " ist nicht kompatibel.");
        }

        List<BackupTableValidationDto> tableReports = new ArrayList<>();
        Map<String, List<Map<String, Object>>> rowsByTable = new LinkedHashMap<>();
        Map<String, Long> manifestCounts = manifestRecordCounts(manifest);
        Set<String> manifestTables = manifestTables(manifest);
        for (BackupTable table : tables) {
            boolean valid = true;
            if (!manifestTables.isEmpty() && !manifestTables.contains(table.logicalName())) {
                errors.add("Tabelle " + table.logicalName() + " fehlt im Manifest.");
                valid = false;
            }
            byte[] content = entries.get(table.fileName());
            if (content == null) {
                errors.add("Datei " + table.fileName() + " fehlt im Backup.");
                tableReports.add(new BackupTableValidationDto(table.logicalName(), 0, false));
                continue;
            }

            List<Map<String, Object>> rows = readRows(content, table, errors);
            if ("categories".equals(table.logicalName())) {
                validateCategoryIcons(rows, errors);
            }
            rowsByTable.put(table.logicalName(), rows);
            long actualCount = rows.size();
            Long expectedCount = manifestCounts.get(table.logicalName());
            if (expectedCount != null && expectedCount != actualCount) {
                errors.add("Record-Count fuer " + table.logicalName()
                        + " stimmt nicht: Manifest=" + expectedCount + ", Datei=" + actualCount + ".");
                valid = false;
            }
            if (rows.stream().anyMatch(row -> !containsColumns(row, table))) {
                errors.add("Datei " + table.fileName() + " enthaelt unvollstaendige Datensaetze.");
                valid = false;
            }
            tableReports.add(new BackupTableValidationDto(table.logicalName(), actualCount, valid));
        }
        validateReferences(rowsByTable, errors);

        BackupValidationReportDto report = new BackupValidationReportDto(
                errors.isEmpty(),
                manifestVersion,
                tableReports,
                warnings,
                errors);
        return new BackupArchive(report, rowsByTable);
    }

    private BackupArchive invalidArchive(String manifestVersion, String error) {
        return new BackupArchive(
                new BackupValidationReportDto(false, manifestVersion, List.of(), List.of(), List.of(error)),
                Map.of());
    }

    private Map<String, byte[]> readZipEntries(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\") || name.contains("..")) {
                    throw new IOException("Unsicherer ZIP-Eintrag: " + name);
                }
                entries.put(name, zip.readAllBytes());
            }
        }
        return entries;
    }

    private Map<String, Object> readManifest(Map<String, byte[]> entries, List<String> errors) {
        byte[] manifestBytes = entries.get("manifest.json");
        if (manifestBytes == null) {
            errors.add("manifest.json fehlt.");
            return null;
        }
        try {
            Map<String, Object> manifest = objectMapper.readValue(manifestBytes, MANIFEST_TYPE);
            if (manifest == null) {
                errors.add("manifest.json ist kein gueltiges JSON-Objekt.");
            }
            return manifest;
        } catch (RuntimeException exception) {
            errors.add("manifest.json ist kein gueltiges JSON-Objekt.");
            return null;
        }
    }

    private List<Map<String, Object>> readRows(byte[] content, BackupTable table, List<String> errors) {
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(content, ROW_LIST_TYPE);
            if (rows == null) {
                errors.add("Datei " + table.fileName() + " ist kein JSON-Array.");
                return List.of();
            }
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    errors.add("Datei " + table.fileName() + " enthaelt Nicht-Objekt-Eintraege.");
                    return List.of();
                }
            }
            return rows;
        } catch (RuntimeException exception) {
            errors.add("Datei " + table.fileName() + " ist kein gueltiges JSON.");
            return List.of();
        }
    }

    private Map<String, Long> manifestRecordCounts(Map<String, Object> manifest) {
        if (manifest == null || !(manifest.get("recordCounts") instanceof Map<?, ?> recordCounts)) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : recordCounts.entrySet()) {
            if (entry.getKey() != null && entry.getValue() instanceof Number number) {
                result.put(entry.getKey().toString(), number.longValue());
            }
        }
        return result;
    }

    private Set<String> manifestTables(Map<String, Object> manifest) {
        if (manifest == null || !(manifest.get("tables") instanceof List<?> tables)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object table : tables) {
            if (table != null) {
                result.add(table.toString());
            }
        }
        return result;
    }

    private boolean containsColumns(Map<String, Object> row, BackupTable table) {
        return table.columns().stream().allMatch(column -> row.containsKey(column.name()));
    }

    private void validateReferences(Map<String, List<Map<String, Object>>> rowsByTable, List<String> errors) {
        Set<String> categoryIds = ids(rowsByTable, "categories");
        Set<String> receiptIds = ids(rowsByTable, "receipts");
        Set<String> receiptItemIds = ids(rowsByTable, "receipt_items");
        Set<String> syncLogIds = ids(rowsByTable, "sync_log");

        validateReference(rowsByTable, "categorization_rules", "category_id", categoryIds, false, errors);
        validateReference(rowsByTable, "receipt_items", "receipt_id", receiptIds, false, errors);
        validateReference(rowsByTable, "receipt_items", "category_id", categoryIds, true, errors);
        validateReference(rowsByTable, "ai_categorization_log", "receipt_item_id", receiptItemIds, false, errors);
        validateReference(rowsByTable, "ai_categorization_log", "assigned_category_id", categoryIds, true, errors);
        validateReference(rowsByTable, "ai_categorization_log", "suggested_category_id", categoryIds, true, errors);
        validateReference(rowsByTable, "sync_log_entry", "sync_log_id", syncLogIds, false, errors);
        validateReference(rowsByTable, "sync_log_entry", "receipt_id", receiptIds, true, errors);
    }

    private void validateCategoryIcons(List<Map<String, Object>> rows, List<String> errors) {
        for (Map<String, Object> row : rows) {
            Object icon = row.get("icon");
            if (icon == null || icon.toString().isBlank()) {
                continue;
            }
            try {
                categoryIconRegistry.normalizeAndValidate(icon.toString());
            } catch (ResponseStatusException exception) {
                errors.add("Kategorie-Icon " + icon + " ist nicht erlaubt.");
            }
        }
    }

    private Set<String> ids(Map<String, List<Map<String, Object>>> rowsByTable, String logicalName) {
        return rowsByTable.getOrDefault(logicalName, List.of()).stream()
                .map(row -> row.get("id"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateReference(
            Map<String, List<Map<String, Object>>> rowsByTable,
            String sourceTable,
            String sourceColumn,
            Set<String> targetIds,
            boolean nullable,
            List<String> errors) {
        for (Map<String, Object> row : rowsByTable.getOrDefault(sourceTable, List.of())) {
            Object rawValue = row.get(sourceColumn);
            if (rawValue == null || rawValue.toString().isBlank()) {
                if (!nullable) {
                    errors.add("Referenz " + sourceTable + "." + sourceColumn + " fehlt.");
                }
                continue;
            }
            if (!targetIds.contains(rawValue.toString())) {
                errors.add("Referenz " + sourceTable + "." + sourceColumn
                        + "=" + rawValue + " zeigt auf keinen Backup-Datensatz.");
                return;
            }
        }
    }

    private String text(Map<String, Object> row, String field) {
        if (row == null) {
            return null;
        }
        Object value = row.get(field);
        return value == null ? null : value.toString();
    }

    private void restoreArchive(BackupArchive archive) {
        try {
            deleteExistingData();
            for (BackupTable table : tables) {
                insertRows(table, archive.rowsByTable().getOrDefault(table.logicalName(), List.of()));
                resetSequence(table);
            }
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Backup-Daten konnten nicht importiert werden.");
        }
    }

    private void deleteExistingData() {
        List.of(
                "sync_log_entry",
                "ai_categorization_log",
                "receipt_item",
                "sync_log",
                "receipt",
                "categorization_rule",
                "parse_rule",
                "app_settings",
                "category"
        ).forEach(table -> jdbcTemplate.update("DELETE FROM " + table));
    }

    private void insertRows(BackupTable table, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        String columns = table.columns().stream()
                .map(BackupColumn::name)
                .reduce((first, second) -> first + ", " + second)
                .orElseThrow();
        String placeholders = table.columns().stream()
                .map(column -> "?::" + column.sqlType())
                .reduce((first, second) -> first + ", " + second)
                .orElseThrow();
        String sql = "INSERT INTO " + table.tableName() + " (" + columns + ") VALUES (" + placeholders + ")";

        for (Map<String, Object> row : rows) {
            Object[] values = table.columns().stream()
                    .map(column -> restoreValue(table, column, row))
                    .toArray();
            jdbcTemplate.update(sql, values);
        }
    }

    private Object restoreValue(BackupTable table, BackupColumn column, Map<String, Object> row) {
        Object value = row.get(column.name());
        if ("app_settings".equals(table.logicalName())
                && "value".equals(column.name())
                && row.get("key") != null
                && SECRET_SETTING_KEYS.contains(row.get("key").toString())
                && Objects.isNull(value)) {
            return "";
        }
        if ("categories".equals(table.logicalName()) && "icon".equals(column.name()) && value != null) {
            return categoryIconRegistry.normalizeAndValidate(value.toString());
        }
        return value;
    }

    private void resetSequence(BackupTable table) {
        if (table.idColumn() == null) {
            return;
        }
        jdbcTemplate.execute(
                "SELECT setval(pg_get_serial_sequence('" + table.tableName() + "', '" + table.idColumn() + "'), "
                        + "greatest(coalesce((SELECT max(" + table.idColumn() + ") FROM " + table.tableName() + "), 1), 1), "
                        + "(SELECT count(*) > 0 FROM " + table.tableName() + "))");
    }

    private static List<BackupTable> backupTables() {
        return List.of(
                table("categories", "category", "categories.json", "id", "id", List.of(
                        col("id", "bigint"), col("name", "varchar"), col("color_hex", "varchar"),
                        col("icon", "varchar"), col("is_active", "boolean"), col("sort_order", "integer"))),
                table("categorization_rules", "categorization_rule", "categorization_rules.json", "id", "id", List.of(
                        col("id", "bigint"), col("category_id", "bigint"), col("match_field", "varchar"),
                        col("match_type", "varchar"), col("match_value", "varchar"), col("priority", "integer"),
                        col("is_active", "boolean"), col("created_at", "timestamptz"))),
                table("parse_rules", "parse_rule", "parse_rules.json", "id", "id", List.of(
                        col("id", "bigint"), col("store_name", "varchar"), col("rule_type", "varchar"),
                        col("match_regex", "varchar"), col("extract_group", "varchar"), col("confidence", "numeric"),
                        col("hit_count", "integer"), col("last_used_at", "timestamptz"), col("source", "varchar"),
                        col("is_active", "boolean"), col("created_at", "timestamptz"))),
                table("receipts", "receipt", "receipts.json", "id", "id", List.of(
                        col("id", "bigint"), col("paperless_document_id", "integer"), col("imported_at", "timestamptz"),
                        col("receipt_date", "date"), col("receipt_time", "time"), col("store_name", "varchar"),
                        col("store_branch", "varchar"), col("total_amount", "numeric"), col("currency", "varchar"),
                        col("raw_text", "text"), col("bonus_balance", "numeric"), col("bonus_points", "numeric"),
                        col("bonus_type", "varchar"), col("parse_status", "varchar"), col("parse_error_message", "text"),
                        col("updated_at", "timestamptz"), col("deleted_at", "timestamptz"), col("delete_reason", "varchar"))),
                table("receipt_items", "receipt_item", "receipt_items.json", "id", "id", List.of(
                        col("id", "bigint"), col("receipt_id", "bigint"), col("position_index", "integer"),
                        col("description", "varchar"), col("quantity", "numeric"), col("unit", "varchar"),
                        col("unit_price", "numeric"), col("total_price", "numeric"), col("discount_amount", "numeric"),
                        col("category_id", "bigint"), col("category_source", "varchar"), col("is_manually_edited", "boolean"),
                        col("updated_at", "timestamptz"))),
                table("ai_categorization_log", "ai_categorization_log", "ai_categorization_log.json", "id", "id", List.of(
                        col("id", "bigint"), col("receipt_item_id", "bigint"), col("prompt_sent", "text"),
                        col("response_received", "text"), col("assigned_category_id", "bigint"),
                        col("ai_confidence", "numeric"), col("model_used", "varchar"), col("created_at", "timestamptz"),
                        col("suggested_category_id", "bigint"), col("suggested_category_name", "varchar"),
                        col("rejection_reason", "varchar"))),
                table("sync_log", "sync_log", "sync_log.json", "id", "id", List.of(
                        col("id", "bigint"), col("started_at", "timestamptz"), col("finished_at", "timestamptz"),
                        col("status", "varchar"), col("new_documents_count", "integer"),
                        col("removed_documents_count", "integer"), col("error_message", "text"))),
                table("sync_log_entry", "sync_log_entry", "sync_log_entry.json", "id", "id", List.of(
                        col("id", "bigint"), col("sync_log_id", "bigint"), col("paperless_document_id", "integer"),
                        col("action", "varchar"), col("receipt_id", "bigint"), col("details", "text"),
                        col("created_at", "timestamptz"))),
                table("app_settings", "app_settings", "app_settings.json", "key", null, List.of(
                        col("key", "varchar"), col("value", "text"), col("description", "text"),
                        col("updated_at", "timestamptz"))));
    }

    private static BackupTable table(
            String logicalName,
            String tableName,
            String fileName,
            String orderBy,
            String idColumn,
            List<BackupColumn> columns) {
        return new BackupTable(logicalName, tableName, fileName, orderBy, idColumn, columns);
    }

    private static BackupColumn col(String name, String sqlType) {
        return new BackupColumn(name, sqlType);
    }

    public record BackupFile(String filename, byte[] content) {
    }

    private record BackupArchive(
            BackupValidationReportDto report,
            Map<String, List<Map<String, Object>>> rowsByTable) {
    }

    private record BackupTable(
            String logicalName,
            String tableName,
            String fileName,
            String orderBy,
            String idColumn,
            List<BackupColumn> columns) {
    }

    private record BackupColumn(String name, String sqlType) {
    }
}
