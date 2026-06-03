package de.ebon.categorization;

import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.CategorizationRule;
import de.ebon.persistence.model.AiCategorizationRejectionReason;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.RuleMatchField;
import de.ebon.persistence.model.RuleMatchType;
import de.ebon.persistence.repository.AiCategorizationLogRepository;
import de.ebon.persistence.repository.CategorizationRuleRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "app.sync.scheduler.enabled=false")
class CategorizationServiceTests extends PostgresIntegrationTestSupport {

    @Autowired
    private CategorizationService categorizationService;

    @Autowired
    private CategoryManagementService categoryManagementService;

    @Autowired
    private FakeAiCategorizationClient aiClient;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategorizationRuleRepository ruleRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReceiptItemRepository receiptItemRepository;

    @Autowired
    private AiCategorizationLogRepository aiLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        aiClient.reset();
        jdbcTemplate.execute("truncate ai_categorization_log, categorization_rule, receipt_item, receipt restart identity cascade");
        jdbcTemplate.update("update app_settings set value = '0.900' where key = 'ai_categorization_min_confidence'");
    }

    @Test
    void ruleWithLowestPriorityWins() {
        Category sonstiges = category("Sonstiges");
        Category lebensmittel = category("Lebensmittel");
        ruleRepository.save(new CategorizationRule(
                sonstiges,
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Milch",
                50));
        ruleRepository.save(new CategorizationRule(
                lebensmittel,
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Milch",
                10));
        Receipt receipt = receipt("REWE", "Bio Milch");

        categorizationService.categorizeReceipt(receipt.getId());

        assertThat(firstItem(receipt).getCategory().getName()).isEqualTo("Lebensmittel");
        assertThat(firstItem(receipt).getCategorySource()).isEqualTo(CategorySource.RULE);
    }

    @Test
    void storeNameAndRegexRulesAreSupported() {
        Category drogerie = category("Drogerie");
        ruleRepository.save(new CategorizationRule(
                drogerie,
                RuleMatchField.STORE_NAME,
                RuleMatchType.REGEX,
                "dm[- ]drogerie|dm",
                10));
        Receipt receipt = receipt("dm-drogerie markt", "Shampoo");

        categorizationService.categorizeReceipt(receipt.getId());

        assertThat(firstItem(receipt).getCategory().getName()).isEqualTo("Drogerie");
        assertThat(firstItem(receipt).getCategorySource()).isEqualTo(CategorySource.RULE);
    }

    @Test
    void withoutOpenRouterApiKeyItemsStayUncategorizedAndAiIsNotCalled() {
        aiClient.available = false;
        Receipt receipt = receipt("REWE", "Unbekannter Artikel");

        categorizationService.categorizeReceipt(receipt.getId());

        assertThat(firstItem(receipt).getCategory()).isNull();
        assertThat(firstItem(receipt).getCategorySource()).isNull();
        assertThat(aiClient.callCount).isZero();
        assertThat(aiLogRepository.count()).isZero();
    }

    @Test
    void knownAiCategorySetsCategorySourceAiAndWritesLog() {
        aiClient.available = true;
        Receipt receipt = receipt("dm", "Shampoo");
        Long itemId = firstItem(receipt).getId();
        aiClient.suggest(itemId, "Drogerie", new BigDecimal("0.900"));

        categorizationService.categorizeReceipt(receipt.getId());

        ReceiptItem item = firstItem(receipt);
        assertThat(item.getCategory().getName()).isEqualTo("Drogerie");
        assertThat(item.getCategorySource()).isEqualTo(CategorySource.AI);
        assertThat(aiClient.lastRequest.minConfidence()).isEqualByComparingTo("0.900");
        assertThat(aiClient.lastRequest.categoryNames()).contains("Drogerie");
        assertThat(aiLogRepository.findAll()).singleElement()
                .satisfies(log -> {
                    assertThat(log.getReceiptItem().getId()).isEqualTo(itemId);
                    assertThat(log.getSuggestedCategory().getName()).isEqualTo("Drogerie");
                    assertThat(log.getSuggestedCategoryName()).isEqualTo("Drogerie");
                    assertThat(log.getAssignedCategory().getName()).isEqualTo("Drogerie");
                    assertThat(log.getAiConfidence()).isEqualByComparingTo("0.900");
                    assertThat(log.getRejectionReason()).isNull();
                });
    }

    @Test
    void configuredAiConfidenceThresholdControlsAcceptedAiCategories() {
        jdbcTemplate.update("update app_settings set value = '0.750' where key = 'ai_categorization_min_confidence'");
        aiClient.available = true;
        Receipt receipt = receipt("dm", "Unscharf aber plausibel");
        Long itemId = firstItem(receipt).getId();
        aiClient.suggest(itemId, "Drogerie", new BigDecimal("0.800"));

        categorizationService.categorizeReceipt(receipt.getId());

        ReceiptItem item = firstItem(receipt);
        assertThat(item.getCategory().getName()).isEqualTo("Drogerie");
        assertThat(item.getCategorySource()).isEqualTo(CategorySource.AI);
        assertThat(aiClient.lastRequest.minConfidence()).isEqualByComparingTo("0.750");
    }

    @Test
    void invalidStoredAiConfidenceFallsBackToDefaultThreshold() {
        jdbcTemplate.update("update app_settings set value = 'abc' where key = 'ai_categorization_min_confidence'");
        aiClient.available = true;
        Receipt receipt = receipt("dm", "Grenzfall Artikel");
        Long itemId = firstItem(receipt).getId();
        aiClient.suggest(itemId, "Drogerie", new BigDecimal("0.850"));

        categorizationService.categorizeReceipt(receipt.getId());

        ReceiptItem item = firstItem(receipt);
        assertThat(item.getCategory()).isNull();
        assertThat(item.getCategorySource()).isNull();
        assertThat(aiClient.lastRequest.minConfidence()).isEqualByComparingTo("0.900");
        assertThat(aiLogRepository.findAll()).singleElement()
                .satisfies(log -> {
                    assertThat(log.getReceiptItem().getId()).isEqualTo(itemId);
                    assertThat(log.getSuggestedCategory().getName()).isEqualTo("Drogerie");
                    assertThat(log.getAssignedCategory()).isNull();
                    assertThat(log.getAiConfidence()).isEqualByComparingTo("0.850");
                    assertThat(log.getRejectionReason()).isEqualTo(AiCategorizationRejectionReason.LOW_CONFIDENCE);
                });
    }

    @Test
    void lowConfidenceAiCategoryKeepsItemUncategorizedAndWritesLogWithoutAssignment() {
        aiClient.available = true;
        Receipt receipt = receipt("dm", "Mehrdeutiger Artikel");
        Long itemId = firstItem(receipt).getId();
        aiClient.suggest(itemId, "Drogerie", new BigDecimal("0.899"));

        categorizationService.categorizeReceipt(receipt.getId());

        ReceiptItem item = firstItem(receipt);
        assertThat(item.getCategory()).isNull();
        assertThat(item.getCategorySource()).isNull();
        assertThat(aiLogRepository.findAll()).singleElement()
                .satisfies(log -> {
                    assertThat(log.getReceiptItem().getId()).isEqualTo(itemId);
                    assertThat(log.getSuggestedCategory().getName()).isEqualTo("Drogerie");
                    assertThat(log.getSuggestedCategoryName()).isEqualTo("Drogerie");
                    assertThat(log.getAssignedCategory()).isNull();
                    assertThat(log.getAiConfidence()).isEqualByComparingTo("0.899");
                    assertThat(log.getRejectionReason()).isEqualTo(AiCategorizationRejectionReason.LOW_CONFIDENCE);
                });
    }

    @Test
    void unknownAiCategoryKeepsItemUncategorizedAndWritesLogWithoutCategory() {
        aiClient.available = true;
        Receipt receipt = receipt("REWE", "Mystery Artikel");
        Long itemId = firstItem(receipt).getId();
        aiClient.suggest(itemId, "Unbekannt", new BigDecimal("0.400"));

        categorizationService.categorizeReceipt(receipt.getId());

        ReceiptItem item = firstItem(receipt);
        assertThat(item.getCategory()).isNull();
        assertThat(item.getCategorySource()).isNull();
        assertThat(aiLogRepository.findAll()).singleElement()
                .satisfies(log -> {
                    assertThat(log.getReceiptItem().getId()).isEqualTo(itemId);
                    assertThat(log.getSuggestedCategory()).isNull();
                    assertThat(log.getSuggestedCategoryName()).isEqualTo("Unbekannt");
                    assertThat(log.getAssignedCategory()).isNull();
                    assertThat(log.getAiConfidence()).isEqualByComparingTo("0.400");
                    assertThat(log.getRejectionReason()).isEqualTo(AiCategorizationRejectionReason.UNKNOWN_CATEGORY);
                });
    }

    @Test
    void invalidAiResponseKeepsItemUncategorizedAndWritesSuggestionLog() {
        aiClient.available = true;
        Receipt receipt = receipt("REWE", "Nicht beantworteter Artikel");

        categorizationService.categorizeReceipt(receipt.getId());

        ReceiptItem item = firstItem(receipt);
        assertThat(item.getCategory()).isNull();
        assertThat(item.getCategorySource()).isNull();
        assertThat(aiLogRepository.findAll()).singleElement()
                .satisfies(log -> {
                    assertThat(log.getReceiptItem().getId()).isEqualTo(item.getId());
                    assertThat(log.getSuggestedCategory()).isNull();
                    assertThat(log.getSuggestedCategoryName()).isNull();
                    assertThat(log.getAssignedCategory()).isNull();
                    assertThat(log.getAiConfidence()).isNull();
                    assertThat(log.getRejectionReason()).isEqualTo(AiCategorizationRejectionReason.INVALID_RESPONSE);
                });
    }

    @Test
    void manualOverrideIsProtectedFromBulkApplyWhileAiAndUncategorizedItemsCanBeUpdated() {
        Category sonstiges = category("Sonstiges");
        Category drogerie = category("Drogerie");
        Category lebensmittel = category("Lebensmittel");
        CategorizationRule rule = ruleRepository.save(new CategorizationRule(
                lebensmittel,
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Milch",
                10));
        Receipt receipt = receipt("REWE", "Bio Milch", "H-Milch", "Frische Milch");
        List<ReceiptItem> items = items(receipt);
        items.get(1).assignCategory(sonstiges, CategorySource.AI);
        categorizationService.manuallyCategorizeItem(items.get(2).getId(), drogerie.getId());

        int changed = categorizationService.applyRuleToExistingItems(rule.getId());

        List<ReceiptItem> reloaded = items(receipt);
        assertThat(changed).isEqualTo(2);
        assertThat(reloaded.get(0).getCategory().getName()).isEqualTo("Lebensmittel");
        assertThat(reloaded.get(0).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(reloaded.get(1).getCategory().getName()).isEqualTo("Lebensmittel");
        assertThat(reloaded.get(1).getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(reloaded.get(2).getCategory().getName()).isEqualTo("Drogerie");
        assertThat(reloaded.get(2).getCategorySource()).isEqualTo(CategorySource.MANUAL);
        assertThat(reloaded.get(2).isManuallyEdited()).isTrue();
    }

    @Test
    void manuallyClearedCategoryStaysUncategorizedAndIsProtectedFromRules() {
        Category lebensmittel = category("Lebensmittel");
        ruleRepository.save(new CategorizationRule(
                lebensmittel,
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Milch",
                10));
        Receipt receipt = receipt("REWE", "Bio Milch");

        categorizationService.categorizeReceipt(receipt.getId());
        ReceiptItem item = firstItem(receipt);
        assertThat(item.getCategory().getName()).isEqualTo("Lebensmittel");

        categorizationService.manuallyClearItemCategory(item.getId());
        categorizationService.categorizeReceipt(receipt.getId());

        ReceiptItem reloaded = firstItem(receipt);
        assertThat(reloaded.getCategory()).isNull();
        assertThat(reloaded.getCategorySource()).isNull();
        assertThat(reloaded.isManuallyEdited()).isTrue();
    }

    @Test
    void categoryDeletePhysicallyDeletesOnlyUnreferencedCategories() {
        Category unused = categoryRepository.saveAndFlush(new Category("Unused", "#111111", "circle", 900));

        CategoryDeletionResult unusedResult = categoryManagementService.deleteOrDeactivateCategory(unused.getId());

        assertThat(unusedResult).isEqualTo(CategoryDeletionResult.HARD_DELETED);
        assertThat(categoryRepository.findById(unused.getId())).isEmpty();

        Category referenced = categoryRepository.saveAndFlush(new Category("Referenced", "#222222", "tag", 901));
        Receipt receipt = receipt("REWE", "Referenz Artikel");
        firstItem(receipt).assignCategory(referenced, CategorySource.MANUAL);

        CategoryDeletionResult referencedResult = categoryManagementService.deleteOrDeactivateCategory(referenced.getId());

        assertThat(referencedResult).isEqualTo(CategoryDeletionResult.DEACTIVATED);
        assertThat(categoryRepository.findById(referenced.getId())).get()
                .satisfies(category -> assertThat(category.isActive()).isFalse());
    }

    private Category category(String name) {
        return categoryRepository.findByName(name).orElseThrow();
    }

    private Receipt receipt(String storeName, String... descriptions) {
        Receipt receipt = new Receipt(100000 + Math.abs(String.join("|", descriptions).hashCode()), "raw text");
        receipt.setStoreName(storeName);
        for (int index = 0; index < descriptions.length; index++) {
            receipt.addItem(new ReceiptItem(index, descriptions[index], new BigDecimal("1.00")));
        }
        return receiptRepository.saveAndFlush(receipt);
    }

    private ReceiptItem firstItem(Receipt receipt) {
        return items(receipt).getFirst();
    }

    private List<ReceiptItem> items(Receipt receipt) {
        return receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId());
    }

    @TestConfiguration
    static class FakeAiCategorizationClientConfig {

        @Bean
        @Primary
        FakeAiCategorizationClient fakeAiCategorizationClient() {
            return new FakeAiCategorizationClient();
        }
    }

    static class FakeAiCategorizationClient implements AiCategorizationClient {

        private boolean available;
        private int callCount;
        private AiCategorizationBatchRequest lastRequest;
        private final Map<Long, AiCategorizationSuggestion> suggestions = new LinkedHashMap<>();

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public AiCategorizationBatchResponse categorize(AiCategorizationBatchRequest request) {
            callCount++;
            lastRequest = request;
            return new AiCategorizationBatchResponse(
                    "fake prompt",
                    "fake response",
                    "fake-model",
                    request.items().stream()
                            .map(item -> suggestions.get(item.itemId()))
                            .filter(java.util.Objects::nonNull)
                            .toList());
        }

        void suggest(Long itemId, String categoryName, BigDecimal confidence) {
            suggestions.put(itemId, new AiCategorizationSuggestion(itemId, categoryName, confidence));
        }

        void reset() {
            available = false;
            callCount = 0;
            lastRequest = null;
            suggestions.clear();
        }
    }
}
