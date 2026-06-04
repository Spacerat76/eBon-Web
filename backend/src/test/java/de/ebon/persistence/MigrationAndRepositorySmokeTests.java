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

    // Verifies Flyway creates required schema objects, reference data, settings, and guarded category seeds.
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

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(10);
        assertThat(receiptItemFtsIndex).isEqualTo("idx_receipt_item_description_fts");
        assertThat(categorySourceConstraint).isEqualTo("chk_receipt_item_category_source_requires_category");
        assertThat(aiRejectionReasonConstraint).isEqualTo("chk_ai_categorization_log_rejection_reason");
        assertThat(categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc())
                .extracting(Category::getName)
                .hasSizeGreaterThanOrEqualTo(22)
                .contains("Fotos & Bilder", "Salat, Obst & Gemüse")
                .doesNotContain("Obst und Gemuese", "Salat");
        assertThat(categorizationRules).isGreaterThan(0);
        assertThat(activeBroadStoreFallbacks).isZero();
        assertThat(appSettingRepository.findById("sync_interval_minutes")).isPresent();
        assertThat(appSettingRepository.findById("ai_model")).isPresent();
        assertThat(appSettingRepository.findById("ai_categorization_min_confidence")).isPresent()
                .get()
                .satisfies(setting -> assertThat(setting.getValue()).isEqualTo("0.900"));
    }

    // Verifies seeded categorization rules cover confirmed real-item cases and leave unknown items uncategorized.
    @Test
    void seededRulesKeepUnknownStoreItemsUncategorized() {
        Receipt receipt = new Receipt(4242, "raw paperless text");
        receipt.setStoreName("REWE");
        receipt.addItem(new ReceiptItem(0, "Brustfilet", new BigDecimal("3.99")));
        receipt.addItem(new ReceiptItem(1, "Rotb. Klassik", new BigDecimal("1.49")));
        receipt.addItem(new ReceiptItem(2, "Lachsfilet", new BigDecimal("8.99")));
        receipt.addItem(new ReceiptItem(3, "Kasseler Lachs", new BigDecimal("4.99")));
        receipt.addItem(new ReceiptItem(4, "HiPP Karotten", new BigDecimal("1.29")));
        receipt.addItem(new ReceiptItem(5, "Fotoauftrag", new BigDecimal("7.95")));
        receipt.addItem(new ReceiptItem(6, "SERVICE GEW", new BigDecimal("2.15")));
        receipt.addItem(new ReceiptItem(7, "TEXAS MIX", new BigDecimal("2.22")));
        receipt.addItem(new ReceiptItem(8, "XXL MISCHBEUT.", new BigDecimal("6.99")));
        receipt.addItem(new ReceiptItem(9, "CLASSIC ROLLE", new BigDecimal("3.98")));
        receipt.addItem(new ReceiptItem(10, "COUNTRY MIX", new BigDecimal("2.99")));
        receipt.addItem(new ReceiptItem(11, "CC SRITE ZERO", new BigDecimal("16.90")));
        receipt.addItem(new ReceiptItem(12, "L CC grat.", new BigDecimal("-16.90")));
        receipt.addItem(new ReceiptItem(13, "FOLIENSTIFT", new BigDecimal("3.29")));
        receipt.addItem(new ReceiptItem(14, "Kulturtasche transparent", new BigDecimal("2.45")));
        receipt.addItem(new ReceiptItem(15, "HIGH PROT. TORT.", new BigDecimal("3.58")));
        receipt.addItem(new ReceiptItem(16, "JOGHURT SCHOKOL.", new BigDecimal("0.99")));
        receipt.addItem(new ReceiptItem(17, "CREME LEGERE", new BigDecimal("1.29")));
        receipt.addItem(new ReceiptItem(18, "HIMBEER M.VANIL.", new BigDecimal("1.49")));
        receipt.addItem(new ReceiptItem(19, "STREICHGUT UNGES", new BigDecimal("2.19")));
        receipt.addItem(new ReceiptItem(20, "SUPER GURKEN", new BigDecimal("1.59")));
        receipt.addItem(new ReceiptItem(21, "ALPEN-MILCHCREME", new BigDecimal("1.79")));
        receipt.addItem(new ReceiptItem(22, "ALPENMILCH 90G", new BigDecimal("1.39")));
        receipt.addItem(new ReceiptItem(23, "SAMT ERDBEERE", new BigDecimal("2.49")));
        receipt.addItem(new ReceiptItem(24, "Unklare Sonderposition", new BigDecimal("2.49")));
        receiptRepository.saveAndFlush(receipt);

        categorizationService.categorizeReceipt(receipt.getId());

        List<ReceiptItem> items = receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId());
        assertThat(items.get(0).getCategory().getName()).isEqualTo("Fleisch und Wurst");
        assertThat(items.get(0).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(1).getCategory().getName()).isEqualTo("Getraenke");
        assertThat(items.get(1).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(2).getCategory().getName()).isEqualTo("Fisch und Meeresfruechte");
        assertThat(items.get(2).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(3).getCategory().getName()).isEqualTo("Fleisch und Wurst");
        assertThat(items.get(3).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(4).getCategory().getName()).isEqualTo("Baby und Kind");
        assertThat(items.get(4).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(5).getCategory().getName()).isEqualTo("Fotos & Bilder");
        assertThat(items.get(5).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(6).getCategory().getName()).isEqualTo("Fleisch und Wurst");
        assertThat(items.get(6).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(7).getCategory().getName()).isEqualTo("Salat, Obst & Gemüse");
        assertThat(items.get(7).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(8).getCategory().getName()).isEqualTo("Suesswaren und Snacks");
        assertThat(items.get(8).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(9).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(9).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(10).getCategory().getName()).isEqualTo("Salat, Obst & Gemüse");
        assertThat(items.get(10).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(11).getCategory().getName()).isEqualTo("Getraenke");
        assertThat(items.get(11).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(12).getCategory().getName()).isEqualTo("Pfand und Rabatte");
        assertThat(items.get(12).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(13).getCategory().getName()).isEqualTo("Freizeit");
        assertThat(items.get(13).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(14).getCategory().getName()).isEqualTo("Koerperpflege");
        assertThat(items.get(14).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(15).getCategory().getName()).isEqualTo("Brot und Backwaren");
        assertThat(items.get(15).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(16).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(16).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(17).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(17).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(18).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(18).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(19).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(19).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(20).getCategory().getName()).isEqualTo("Suesswaren und Snacks");
        assertThat(items.get(20).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(21).getCategory().getName()).isEqualTo("Suesswaren und Snacks");
        assertThat(items.get(21).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(22).getCategory().getName()).isEqualTo("Suesswaren und Snacks");
        assertThat(items.get(22).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(23).getCategory().getName()).isEqualTo("Vorrat und Fertiggerichte");
        assertThat(items.get(23).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(24).getCategory()).isNull();
        assertThat(items.get(24).getCategorySource()).isNull();
    }

    // Verifies repository persistence keeps receipt items and implements TAG_REMOVED as a soft delete.
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

    // Verifies categories can be deactivated so referenced categories are not unsafely hard-deleted.
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
