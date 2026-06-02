package de.ebon.persistence;

import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.AppSettingRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MigrationAndRepositorySmokeTests extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Test
    void flywayCreatesSchemaAndReferenceData() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class);
        String receiptItemFtsIndex = jdbcTemplate.queryForObject(
                "select to_regclass('idx_receipt_item_description_fts')::text",
                String.class);
        String categorySourceConstraint = jdbcTemplate.queryForObject(
                """
                        select conname
                        from pg_constraint
                        where conname = 'chk_receipt_item_category_source_requires_category'
                        """,
                String.class);
        Integer categorizationRules = jdbcTemplate.queryForObject(
                "select count(*) from categorization_rule",
                Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(4);
        assertThat(receiptItemFtsIndex).isEqualTo("idx_receipt_item_description_fts");
        assertThat(categorySourceConstraint).isEqualTo("chk_receipt_item_category_source_requires_category");
        assertThat(categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(categorizationRules).isGreaterThan(0);
        assertThat(appSettingRepository.findById("sync_interval_minutes")).isPresent();
        assertThat(appSettingRepository.findById("ai_model")).isPresent();
    }

    @Test
    void receiptRepositoryPersistsItemsAndSupportsSoftDelete() {
        Category category = categoryRepository.findByName("Sonstiges").orElseThrow();
        Receipt receipt = new Receipt(42, "raw paperless text");
        receipt.setStoreName("Test Store");

        ReceiptItem item = new ReceiptItem(0, "Test Artikel", new BigDecimal("2.99"));
        item.assignCategory(category, CategorySource.MANUAL);
        receipt.addItem(item);

        Receipt saved = receiptRepository.saveAndFlush(receipt);

        assertThat(receiptRepository.findByPaperlessDocumentId(42)).isPresent();
        assertThat(receiptRepository.findByDeletedAtIsNullOrderByImportedAtDesc())
                .extracting(Receipt::getId)
                .contains(saved.getId());

        saved.markDeleted(DeleteReason.TAG_REMOVED);
        receiptRepository.saveAndFlush(saved);

        Receipt deleted = receiptRepository.findByPaperlessDocumentId(42).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getDeleteReason()).isEqualTo(DeleteReason.TAG_REMOVED);
        assertThat(receiptRepository.findByDeletedAtIsNullOrderByImportedAtDesc())
                .extracting(Receipt::getId)
                .doesNotContain(saved.getId());
    }

    @Test
    void categoryCanBeDeactivatedInsteadOfHardDeleted() {
        Category category = categoryRepository.findByName("Lebensmittel").orElseThrow();

        category.deactivate();
        categoryRepository.saveAndFlush(category);

        Category reloaded = categoryRepository.findByName("Lebensmittel").orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc())
                .extracting(Category::getName)
                .doesNotContain("Lebensmittel");
    }
}
