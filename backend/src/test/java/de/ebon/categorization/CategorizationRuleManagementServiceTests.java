package de.ebon.categorization;

import de.ebon.api.dto.CategorizationRuleDto;
import de.ebon.api.dto.CategorizationRulePreviewRequest;
import de.ebon.api.dto.CategorizationRuleRequest;
import de.ebon.persistence.model.CategorizationRule;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.RuleMatchField;
import de.ebon.persistence.model.RuleMatchType;
import de.ebon.persistence.repository.CategorizationRuleRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "app.sync.scheduler.enabled=false")
class CategorizationRuleManagementServiceTests extends PostgresIntegrationTestSupport {

    @Autowired
    private CategorizationRuleManagementService ruleManagementService;

    @Autowired
    private CategorizationRuleRepository ruleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReceiptItemRepository receiptItemRepository;

    @Autowired
    private CategorizationService categorizationService;

    private int paperlessDocumentId = 700000;

    @BeforeEach
    void setUp() {
        ruleRepository.deleteAll();
        receiptItemRepository.deleteAll();
        receiptRepository.deleteAll();
        receiptRepository.flush();
    }

    @Test
    void createDefaultsPriorityDeactivatesAndAppliesToExistingWhenRequested() {
        Category category = category("Lebensmittel");
        receiptWithItem("REWE", "Bio Milch 1l");

        when(categorizationService.applyRuleToExistingItems(org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);

        CategorizationRuleDto rule = ruleManagementService.create(new CategorizationRuleRequest(
                category.getId(),
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Milch",
                null,
                Boolean.FALSE,
                Boolean.TRUE));

        assertThat(rule.priority()).isEqualTo(100);
        assertThat(rule.isActive()).isFalse();
        verify(categorizationService).applyRuleToExistingItems(rule.id());
        assertThat(ruleRepository.findById(rule.id())).isPresent();
    }

    @Test
    void updateHonorsExplicitPriorityActivationAndApplyToExisting() {
        Category category = category("Koerperpflege");
        CategorizationRule rule = ruleRepository.saveAndFlush(new CategorizationRule(
                category,
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Shampoo",
                250));

        when(categorizationService.applyRuleToExistingItems(org.mockito.ArgumentMatchers.anyLong())).thenReturn(2);

        CategorizationRuleDto updated = ruleManagementService.update(rule.getId(), new CategorizationRuleRequest(
                category.getId(),
                RuleMatchField.STORE_NAME,
                RuleMatchType.STARTS_WITH,
                "DM",
                75,
                Boolean.TRUE,
                Boolean.TRUE));

        assertThat(updated.matchField()).isEqualTo(RuleMatchField.STORE_NAME);
        assertThat(updated.matchType()).isEqualTo(RuleMatchType.STARTS_WITH);
        assertThat(updated.priority()).isEqualTo(75);
        assertThat(updated.isActive()).isTrue();
        verify(categorizationService).applyRuleToExistingItems(rule.getId());
    }

    @Test
    void previewUsesFirstActiveCategoryWhenCategoryIdIsMissingAndSkipsManualItems() {
        Category active = category("Brot und Backwaren");
        Category inactive = category("Freizeit");
        inactive.deactivate();
        categoryRepository.saveAndFlush(inactive);

        receiptWithItem("REWE", "Protein Tortilla");
        receiptWithItem("REWE", "Protein Tortilla Manuell", CategorySource.MANUAL, active);
        receiptWithItem("REWE", "Protein Tortilla AI", CategorySource.AI, active);
        receiptWithItem("REWE", "Apfel");

        long count = ruleManagementService.preview(new CategorizationRulePreviewRequest(
                null,
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Tortilla"));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void previewWithExplicitCategoryAndCreateOnInactiveCategoryFails() {
        Category category = category("Getraenke");
        ReceiptItem item = receiptWithItem("REWE", "Cola Zero");

        long count = ruleManagementService.preview(new CategorizationRulePreviewRequest(
                category.getId(),
                RuleMatchField.STORE_NAME,
                RuleMatchType.CONTAINS,
                "REWE"));

        assertThat(count).isEqualTo(1);

        category.deactivate();
        categoryRepository.saveAndFlush(category);

        assertThatThrownBy(() -> ruleManagementService.create(new CategorizationRuleRequest(
                category.getId(),
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Cola",
                10,
                Boolean.TRUE,
                Boolean.FALSE))).isInstanceOf(IllegalArgumentException.class);

        assertThat(item.getDescription()).isEqualTo("Cola Zero");
    }

    @Test
    void deleteAndApplyRequireExistingRule() {
        Category category = category("Haushalt");
        CategorizationRule rule = ruleRepository.saveAndFlush(new CategorizationRule(
                category,
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Stift",
                40));

        when(categorizationService.applyRuleToExistingItems(rule.getId())).thenReturn(3);

        assertThat(ruleManagementService.apply(rule.getId())).isEqualTo(3);
        ruleManagementService.delete(rule.getId());
        assertThat(ruleRepository.findById(rule.getId())).isEmpty();
    }

    @Test
    void missingRuleAndMissingActiveCategoryFailAsExpected() {
        assertThatThrownBy(() -> ruleManagementService.update(999999L, new CategorizationRuleRequest(
                category("Lebensmittel").getId(),
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Milch",
                10,
                Boolean.TRUE,
                Boolean.FALSE))).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);

        categoryRepository.findAll().forEach(Category::deactivate);
        categoryRepository.flush();
        assertThatThrownBy(() -> ruleManagementService.preview(new CategorizationRulePreviewRequest(
                null,
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Milch"))).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    private Category category(String name) {
        return categoryRepository.findByName(name).orElseThrow();
    }

    private ReceiptItem receiptWithItem(String storeName, String itemDescription) {
        return receiptWithItem(storeName, itemDescription, null, null);
    }

    private ReceiptItem receiptWithItem(String storeName, String itemDescription, CategorySource source, Category category) {
        Receipt receipt = new Receipt(paperlessDocumentId++, "raw");
        receipt.applyParseResult(
                ParseStatus.PARSED,
                null,
                LocalDate.of(2026, 6, 3),
                LocalTime.NOON,
                storeName,
                null,
                BigDecimal.ONE,
                "EUR",
                null,
                null,
                null);
        ReceiptItem item = new ReceiptItem(0, itemDescription, BigDecimal.ONE);
        if (category != null && source != null) {
            item.assignCategory(category, source);
        }
        receipt.addItem(item);
        receiptRepository.saveAndFlush(receipt);
        return item;
    }

    @TestConfiguration
    static class FakeCategorizationServiceConfig {

        @Bean
        @Primary
        CategorizationService categorizationService() {
            return mock(CategorizationService.class);
        }
    }
}
