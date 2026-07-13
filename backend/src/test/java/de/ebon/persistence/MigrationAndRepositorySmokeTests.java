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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
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

    @Autowired
    private EntityManager entityManager;

    @Test
    void confirmedOpenItemRulesIncludeReweConstraintAndExcludeOriginal() {
        Integer confirmedRules = jdbcTemplate.queryForObject(
                """
                select count(*)
                from categorization_rule r
                join category c on c.id = r.category_id
                where r.match_type = 'EXACT'
                  and r.match_field = 'DESCRIPTION'
                  and r.match_value in (
                    'SPARERIBS','RD HUEFTE','ROULADE FRZ','KIPA GEF. VEGAN','Nasi Goreng','Baml Goreng',
                    'CORNICHONS KRAEU','DELIKATESS SENF','TAFELMEERRETTICH','TRIOLADE','Verano Vanilla',
                    'FH-DOSE 450ML','TIEFKUEHLTASCHE','Paradies Baby C Power','Paradies Micro AAA 4 St',
                    'Mayben B&K Sonnencreme 100ml','SauBär Badezubehör Pad','essence Nagelkleber fix it!',
                    'o.b.ExtraProtect Super 42St','LEBENSMITTEL','BEDIENUNGSTHEKE'
                  )
                """, Integer.class);
        Integer originalRule = jdbcTemplate.queryForObject(
                "select count(*) from categorization_rule where lower(match_value) = 'original'",
                Integer.class);

        assertThat(confirmedRules).isEqualTo(21);
        assertThat(originalRule).isZero();
    }

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
        Integer activeGenericPaintRules = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from categorization_rule rule
                        join category category on category.id = rule.category_id
                        where category.name = 'Baumarkt und Garten'
                            and rule.match_field = 'DESCRIPTION'
                            and rule.match_type = 'CONTAINS'
                            and upper(rule.match_value) = 'FARBE'
                            and rule.is_active = true
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
        assertThat(activeGenericPaintRules).isZero();
        assertThat(appSettingRepository.findById("sync_interval_minutes")).isPresent();
        assertThat(appSettingRepository.findById("ai_model")).isPresent();
        assertThat(appSettingRepository.findById("ai_categorization_min_confidence")).isPresent()
                .get()
                .satisfies(setting -> assertThat(setting.getValue()).isEqualTo("0.900"));
    }

    // Verifies the Phase 15a schema provides product master data, assignments, and conservative history defaults.
    @Test
    void flywayCreatesProductAssignmentFoundation() {
        Integer productTableCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'public'
                          and table_name in ('product_family', 'product_variant', 'product_rule', 'product_assignment_log')
                        """,
                Integer.class);
        Integer receiptItemProductColumnCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'receipt_item'
                          and column_name in (
                              'product_family_id', 'product_variant_id', 'product_assignment_source',
                              'product_assignment_status', 'product_assignment_confidence', 'product_assignment_updated_at',
                              'exclude_from_product_price_comparison', 'product_price_exclusion_reason')
                        """,
                Integer.class);

        assertThat(productTableCount).isEqualTo(4);
        assertThat(receiptItemProductColumnCount).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "select value from app_settings where key = 'product_history_min_confirmed_matches'",
                String.class)).isEqualTo("3");
        assertThat(jdbcTemplate.queryForObject(
                "select value from app_settings where key = 'product_history_min_variant_share'",
                String.class)).isEqualTo("0.900");
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
        receipt.addItem(new ReceiptItem(24, "FR.JOG.NATUR 1,5", new BigDecimal("1.09")));
        receipt.addItem(new ReceiptItem(25, "DUO VLA SCH./VA.", new BigDecimal("1.49")));
        receipt.addItem(new ReceiptItem(26, "754753 FF Ungarisch 175g", new BigDecimal("0.99")));
        receipt.addItem(new ReceiptItem(27, "297776 Chocr Sahne&amp;Weiße", new BigDecimal("2.19")));
        receipt.addItem(new ReceiptItem(28, "701900 Ba Schlupfhose", new BigDecimal("5.99")));
        receipt.addItem(new ReceiptItem(29, "702620 KQRo XXL 31g.12x64", new BigDecimal("6.99")));
        receipt.addItem(new ReceiptItem(30, "Büsch auf 30Die.", new BigDecimal("2.40")));
        receipt.addItem(new ReceiptItem(31, "HINTERSCHINK", new BigDecimal("2.62")));
        receipt.addItem(new ReceiptItem(32, "Herz.Parmigiano3,29 € x 2", new BigDecimal("6.58")));
        receipt.addItem(new ReceiptItem(33, "Landl.Erdb.Konfi.", new BigDecimal("2.49")));
        receipt.addItem(new ReceiptItem(34, "ALT BV", new BigDecimal("10.99")));
        receipt.addItem(new ReceiptItem(35, "JA GRAN DUETT", new BigDecimal("0.98")));
        receipt.addItem(new ReceiptItem(36, "SENF MITTELSCH.", new BigDecimal("1.69")));
        receipt.addItem(new ReceiptItem(37, "MIRACEL WHIP", new BigDecimal("1.69")));
        receipt.addItem(new ReceiptItem(38, "CORNICHONS CHILI", new BigDecimal("0.99")));
        receipt.addItem(new ReceiptItem(39, "ORIGINAL NFB", new BigDecimal("3.29")));
        receipt.addItem(new ReceiptItem(40, "Nesquik Original", new BigDecimal("2.29")));
        receipt.addItem(new ReceiptItem(41, "BABY-TOPS Groesse 074", new BigDecimal("4.99")));
        receipt.addItem(new ReceiptItem(42, "Unklare Sonderposition", new BigDecimal("2.49")));
        receipt.addItem(new ReceiptItem(43, "BABY-HOSE Farbe 1 Große 074", new BigDecimal("5.99")));
        receipt.addItem(new ReceiptItem(44, "J BB TROUSERS Farbe 1 Große 068", new BigDecimal("6.99")));
        receipt.addItem(new ReceiptItem(45, "Neutraler Artikel Farbe 1 Große 047", new BigDecimal("1.99")));
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
        assertThat(items.get(24).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(24).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(25).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(25).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(26).getCategory().getName()).isEqualTo("Suesswaren und Snacks");
        assertThat(items.get(26).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(27).getCategory().getName()).isEqualTo("Suesswaren und Snacks");
        assertThat(items.get(27).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(28).getCategory().getName()).isEqualTo("Baby und Kind");
        assertThat(items.get(28).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(29).getCategory().getName()).isEqualTo("Haushalt");
        assertThat(items.get(29).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(30).getCategory().getName()).isEqualTo("Brot und Backwaren");
        assertThat(items.get(30).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(31).getCategory().getName()).isEqualTo("Fleisch und Wurst");
        assertThat(items.get(31).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(32).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(32).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(33).getCategory().getName()).isEqualTo("Vorrat und Fertiggerichte");
        assertThat(items.get(33).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(34).getCategory().getName()).isEqualTo("Getraenke");
        assertThat(items.get(34).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(35).getCategory().getName()).isEqualTo("Milchprodukte und Eier");
        assertThat(items.get(35).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(36).getCategory().getName()).isEqualTo("Vorrat und Fertiggerichte");
        assertThat(items.get(36).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(37).getCategory().getName()).isEqualTo("Vorrat und Fertiggerichte");
        assertThat(items.get(37).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(38).getCategory().getName()).isEqualTo("Vorrat und Fertiggerichte");
        assertThat(items.get(38).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(39).getCategory().getName()).isEqualTo("Suesswaren und Snacks");
        assertThat(items.get(39).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(40).getCategory().getName()).isEqualTo("Getraenke");
        assertThat(items.get(40).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(41).getCategory().getName()).isEqualTo("Baby und Kind");
        assertThat(items.get(41).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(42).getCategory()).isNull();
        assertThat(items.get(42).getCategorySource()).isNull();
        assertThat(items.get(43).getCategory().getName()).isEqualTo("Baby und Kind");
        assertThat(items.get(43).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(44).getCategory().getName()).isEqualTo("Baby und Kind");
        assertThat(items.get(44).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(45).getCategory()).isNull();
        assertThat(items.get(45).getCategorySource()).isNull();
    }

    // Verifies the confirmed real-receipt rules stay specific and leave ambiguous labels open for review.
    @Test
    void seededRulesCategorizeConfirmedJawollAndCaItemsWithoutBroadFallbacks() {
        Receipt receipt = new Receipt(4245, "raw paperless text");
        receipt.setStoreName("Jawoll");
        receipt.addItem(new ReceiptItem(0, "B-OUTDOOR Große 074", new BigDecimal("13.99")));
        receipt.addItem(new ReceiptItem(1, "B-WAESCHE Große 074", new BigDecimal("9.99")));
        receipt.addItem(new ReceiptItem(2, "E.Regio.Papr.Lyon.", new BigDecimal("1.00")));
        receipt.addItem(new ReceiptItem(3, "Versch.Sorten", new BigDecimal("2.22")));
        receipt.addItem(new ReceiptItem(4, "G&G B.O.Butte", new BigDecimal("1.18")));
        receipt.addItem(new ReceiptItem(5, "Bounty Minis", new BigDecimal("2.69")));
        receipt.addItem(new ReceiptItem(6, "Super Dickmanns 9er", new BigDecimal("2.49")));
        receipt.addItem(new ReceiptItem(7, "Chlortaß-Super 1kg", new BigDecimal("9.99")));
        receipt.addItem(new ReceiptItem(8, "Edelgeranie 23cm", new BigDecimal("9.99")));
        receipt.addItem(new ReceiptItem(9, "Wellenbox 17L", new BigDecimal("15.96")));
        receipt.addItem(new ReceiptItem(10, "Windrad bunt mit Bia", new BigDecimal("3.99")));
        receipt.addItem(new ReceiptItem(11, "Da/He Bio Pantolette", new BigDecimal("6.66")));
        receipt.addItem(new ReceiptItem(12, "ORIGINAL", new BigDecimal("1.39")));
        receipt.addItem(new ReceiptItem(13, "LEBENSMITTEL", new BigDecimal("1.98")));
        receiptRepository.saveAndFlush(receipt);

        categorizationService.categorizeReceipt(receipt.getId());

        List<ReceiptItem> items = receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId());
        assertThat(items)
                .extracting(item -> item.getCategory() == null ? null : item.getCategory().getName())
                .containsExactly(
                        "Baby und Kind",
                        "Baby und Kind",
                        "Fleisch und Wurst",
                        "Fleisch und Wurst",
                        "Milchprodukte und Eier",
                        "Suesswaren und Snacks",
                        "Suesswaren und Snacks",
                        "Baumarkt und Garten",
                        "Baumarkt und Garten",
                        "Haushalt",
                        "Baumarkt und Garten",
                        "Kleidung und Schuhe",
                        null,
                        "Lebensmittel");
        assertThat(items.subList(0, 12)).allSatisfy(item -> {
            assertThat(item.getCategorySource()).isEqualTo(CategorySource.RULE);
            assertThat(item.isManuallyEdited()).isFalse();
        });
        assertThat(items.get(12).getCategory()).isNull();
        assertThat(items.get(12).getCategorySource()).isNull();
        assertThat(items.get(13).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(items.get(13).isManuallyEdited()).isFalse();
    }

    // Verifies V22 repairs C&A rule assignments created before its product-specific rules existed.
    @Test
    void caMigrationRepairsHistoricalRuleAssignmentsWithoutOverwritingManualItems() {
        Category hardwareCategory = categoryRepository.findByName("Baumarkt und Garten").orElseThrow();
        Category babyCategory = categoryRepository.findByName("Baby und Kind").orElseThrow();
        Receipt receipt = new Receipt(4244, "historical C&A receipt");
        receipt.setStoreName("C&A");
        receipt.addItem(ruleCategorizedItem(0, "KI-TAGESW Farbe 1 Große 068", hardwareCategory));
        receipt.addItem(ruleCategorizedItem(1, "BABY-TOPS Farbe 1 Große 074", hardwareCategory));
        receipt.addItem(ruleCategorizedItem(2, "BABY-COMBI Farbe 1 Große 074", hardwareCategory));
        receipt.addItem(ruleCategorizedItem(3, "B-ACCESS Farbe 1 Große 070", hardwareCategory));
        receipt.addItem(ruleCategorizedItem(4, "J BB TOPS Farbe 1 Große 074", hardwareCategory));
        receipt.addItem(ruleCategorizedItem(5, "BABY-HOSE Farbe 1 Große 074", hardwareCategory));
        ReceiptItem manuallyEditedItem = new ReceiptItem(6, "J BB TROUSERS Farbe 1 Große 068", new BigDecimal("5.99"));
        manuallyEditedItem.assignCategory(hardwareCategory, CategorySource.MANUAL);
        receipt.addItem(manuallyEditedItem);
        receiptRepository.saveAndFlush(receipt);

        Connection connection = DataSourceUtils.getConnection(jdbcTemplate.getDataSource());
        try {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V22__refine_ca_color_size_categorization.sql"));
        } finally {
            DataSourceUtils.releaseConnection(connection, jdbcTemplate.getDataSource());
        }

        entityManager.clear();
        List<ReceiptItem> items = receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId());
        assertThat(items.subList(0, 6)).allSatisfy(item -> {
            assertThat(item.getCategory().getName()).isEqualTo(babyCategory.getName());
            assertThat(item.getCategorySource()).isEqualTo(CategorySource.RULE);
            assertThat(item.isManuallyEdited()).isFalse();
        });
        assertThat(items.get(6).getCategory().getName()).isEqualTo(hardwareCategory.getName());
        assertThat(items.get(6).getCategorySource()).isEqualTo(CategorySource.MANUAL);
        assertThat(items.get(6).isManuallyEdited()).isTrue();
    }

    private ReceiptItem ruleCategorizedItem(int positionIndex, String description, Category category) {
        ReceiptItem item = new ReceiptItem(positionIndex, description, new BigDecimal("5.99"));
        item.assignCategory(category, CategorySource.RULE);
        return item;
    }

    // Verifies restaurant store context wins over grocery-oriented product-name rules such as hamburger buns.
    @Test
    void seededRulesCategorizeMcDonaldsReceiptAsGastronomy() {
        Receipt receipt = new Receipt(4243, "raw paperless text");
        receipt.setStoreName("McDonald's");
        receipt.addItem(new ReceiptItem(0, "Cheeseburger", new BigDecimal("7.50")));
        receipt.addItem(new ReceiptItem(1, "Hamburger Royal TS", new BigDecimal("6.99")));
        receipt.addItem(new ReceiptItem(2, "2x Filet-o-Fish®", new BigDecimal("5.00")));
        receiptRepository.saveAndFlush(receipt);

        categorizationService.categorizeReceipt(receipt.getId());

        List<ReceiptItem> items = receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId());
        assertThat(items).hasSize(3);
        assertThat(items)
                .extracting(item -> item.getCategory().getName())
                .containsExactly("Gastronomie", "Gastronomie", "Gastronomie");
        assertThat(items)
                .extracting(ReceiptItem::getCategorySource)
                .containsExactly(CategorySource.RULE, CategorySource.RULE, CategorySource.RULE);
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
