package de.ebon.persistence;

import de.ebon.categorization.CategorizationService;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.AppSettingRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.util.List;
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

    @Autowired
    private ReceiptItemRepository receiptItemRepository;

    @Autowired
    private CategorizationService categorizationService;

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
        String aiRejectionReasonConstraint = jdbcTemplate.queryForObject(
                """
                        select conname
                        from pg_constraint
                        where conname = 'chk_ai_categorization_log_rejection_reason'
                        """,
                String.class);
        Integer categorizationRules = jdbcTemplate.queryForObject(
                "select count(*) from categorization_rule",
                Integer.class);
        Integer activeBroadStoreFallbacks = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from categorization_rule
                        where match_field = 'STORE_NAME'
                            and match_type = 'CONTAINS'
                            and priority = 900
                            and is_active = true
                        """,
                Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(6);
        assertThat(receiptItemFtsIndex).isEqualTo("idx_receipt_item_description_fts");
        assertThat(categorySourceConstraint).isEqualTo("chk_receipt_item_category_source_requires_category");
        assertThat(aiRejectionReasonConstraint).isEqualTo("chk_ai_categorization_log_rejection_reason");
        assertThat(categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(categorizationRules).isGreaterThan(0);
        assertThat(activeBroadStoreFallbacks).isZero();
        assertThat(appSettingRepository.findById("sync_interval_minutes")).isPresent();
        assertThat(appSettingRepository.findById("ai_model")).isPresent();
        assertThat(appSettingRepository.findById("ai_categorization_min_confidence")).isPresent()
                .get()
                .satisfies(setting -> assertThat(setting.getValue()).isEqualTo("0.900"));
    }

    @Test
    void seededRulesKeepUnknownStoreItemsUncategorized() {
        Receipt receipt = new Receipt(4242, "raw paperless text");
        receipt.setStoreName("REWE");
        receipt.addItem(new ReceiptItem(0, "Brustfilet", new BigDecimal("3.99")));
        receipt.addItem(new ReceiptItem(1, "Rotb. Klassik", new BigDecimal("1.49")));
        receipt.addItem(new ReceiptItem(2, "Lachsfilet", new BigDecimal("8.99")));
        receipt.addItem(new ReceiptItem(3, "Unklare Sonderposition", new BigDecimal("2.49")));
        receiptRepository.saveAndFlush(receipt);

        categorizationService.categorizeReceipt(receipt.getId());

        List<ReceiptItem> items = receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId());
        assertThat(items.get(0).getCategory().getName()).isEqualTo("Fleisch und Wurst");
        assertThat(items.get(0).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(1).getCategory().getName()).isEqualTo("Getraenke");
        assertThat(items.get(1).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(2).getCategory().getName()).isEqualTo("Fisch und Meeresfruechte");
        assertThat(items.get(2).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(3).getCategory()).isNull();
        assertThat(items.get(3).getCategorySource()).isNull();
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
