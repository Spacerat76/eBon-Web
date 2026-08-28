package de.ebon.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductRule;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.RuleMatchType;
import de.ebon.persistence.repository.AppSettingRepository;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductRuleRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductAssignmentServiceTests {

    @Test
    void uncertainExtractionIsExcludedFromProductRulesAndBulkAssignment() {
        ProductFamily family = new ProductFamily("Milch", null);
        ProductRule rule = new ProductRule(family, null, null, RuleMatchType.EXACT, "Milch", 1);
        Receipt receipt = new Receipt(990001, "raw");
        ReceiptItem uncertain = new ReceiptItem(0, "Milch", BigDecimal.ONE);
        uncertain.setExtractionStatus(de.ebon.persistence.model.ExtractionStatus.NEEDS_REVIEW);
        ReceiptItem confirmed = new ReceiptItem(1, "Milch", BigDecimal.ONE);
        receipt.addItem(uncertain);
        receipt.addItem(confirmed);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(rule));
        when(receiptItemRepository.findAll()).thenReturn(List.of(uncertain, confirmed));

        assertThat(productAssignmentService().assignItems(receipt, List.of(uncertain))).isZero();
        assertThat(productAssignmentService().applyRuleToExistingItems(rule)).isEqualTo(1);
        assertThat(uncertain.getProductFamily()).isNull();
        assertThat(confirmed.getProductFamily()).isSameAs(family);
    }

    @Test
    void uncertainExtractionNeverEntersHistoryAiOrNonProductAutomation() {
        Receipt receipt = new Receipt(990002, "raw");
        ReceiptItem uncertain = new ReceiptItem(0, "Milch", BigDecimal.ONE);
        ReceiptItem coupon = new ReceiptItem(1, "Coupon", BigDecimal.ONE.negate());
        for (ReceiptItem item : List.of(uncertain, coupon)) {
            item.setExtractionStatus(de.ebon.persistence.model.ExtractionStatus.NEEDS_REVIEW);
            receipt.addItem(item);
        }
        assertThat(productAssignmentService().assignItems(receipt, receipt.getItems())).isZero();
        assertThat(uncertain.getProductAssignmentStatus()).isNull();
        assertThat(coupon.getProductAssignmentStatus()).isNull();
        org.mockito.Mockito.verifyNoInteractions(receiptItemRepository, aiProductAssignmentClient,
                productAssignmentLogRepository);
    }

    @Test
    void uncertainHistoricExtractionCannotTeachAConfirmedItem() {
        ProductFamily family = new ProductFamily("Milch", null);
        ProductVariant variant = variant(family, "Milch 1 l", BigDecimal.ONE, "l");
        List<ReceiptItem> history = java.util.stream.IntStream.range(0, 3)
                .mapToObj(i -> trustedItem("Milch", "REWE", variant, BigDecimal.ONE, "l")).toList();
        history.forEach(item -> item.setExtractionStatus(de.ebon.persistence.model.ExtractionStatus.NEEDS_REVIEW));
        Receipt receipt = new Receipt(990003, "raw");
        receipt.setStoreName("REWE");
        ReceiptItem target = new ReceiptItem(0, "Milch", BigDecimal.ONE);
        receipt.addItem(target);
        when(receiptItemRepository.findAll()).thenReturn(history);
        productAssignmentService().assignItems(receipt, List.of(target));
        assertThat(target.getProductFamily()).isNull();
        assertThat(target.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.NEEDS_REVIEW);
    }

    @Mock
    private ProductRuleRepository productRuleRepository;

    @Mock
    private ProductFamilyRepository productFamilyRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductAssignmentLogRepository productAssignmentLogRepository;

    @Mock
    private ReceiptItemRepository receiptItemRepository;

    @Mock
    private AppSettingRepository appSettingRepository;

    @Mock
    private AiProductAssignmentClient aiProductAssignmentClient;

    @Test
    void storeSpecificRuleOverridesGlobalRuleForTheSameDescription() {
        ProductFamily globalFamily = new ProductFamily("Hamburger-Brötchen", null);
        ProductFamily restaurantFamily = new ProductFamily("McDonald's Cheeseburger", null);
        ProductVariant restaurantVariant = new ProductVariant(
                restaurantFamily,
                "McDonald's Cheeseburger einzeln",
                BigDecimal.ONE,
                "piece",
                1,
                null,
                BigDecimal.ONE,
                "piece",
                null);
        ProductRule globalRule = new ProductRule(
                globalFamily,
                null,
                null,
                RuleMatchType.CONTAINS,
                "Cheeseburger",
                10);
        ProductRule storeRule = new ProductRule(
                restaurantFamily,
                restaurantVariant,
                "McDonald's",
                RuleMatchType.CONTAINS,
                "Cheeseburger",
                100);
        Receipt receipt = new Receipt(900001, "mock raw text");
        receipt.setStoreName("McDonald's");
        ReceiptItem item = new ReceiptItem(0, "Cheeseburger", new BigDecimal("2.29"));
        receipt.addItem(item);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(globalRule, storeRule));

        ProductAssignmentService service = new ProductAssignmentService(
                productRuleRepository,
                productFamilyRepository,
                productVariantRepository,
                receiptItemRepository,
                productAssignmentLogRepository,
                appSettingRepository,
                new ProductRuleMatcher(),
                new ProductUnitNormalizer(),
                aiProductAssignmentClient);

        service.assignItems(receipt, List.of(item));

        assertThat(item.getProductFamily()).isSameAs(restaurantFamily);
        assertThat(item.getProductVariant()).isSameAs(restaurantVariant);
        assertThat(item.getProductAssignmentSource()).isEqualTo(ProductAssignmentSource.RULE);
        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.AUTO_ASSIGNED);
    }

    // Verifies three trusted, same-size assignments establish a conservative history match for a new item.
    @Test
    void clearTrustedHistoryAssignsTheDominantVariant() {
        ProductFamily family = new ProductFamily("Bio Milch", null);
        ProductVariant variant = variant(family, "Bio Milch 1 l", new BigDecimal("1.000"), "l");
        ReceiptItem historicalOne = trustedItem("Bio Milch", "REWE", variant, new BigDecimal("1.000"), "l");
        ReceiptItem historicalTwo = trustedItem("Bio Milch", "REWE", variant, new BigDecimal("1.000"), "l");
        ReceiptItem historicalThree = trustedItem("Bio Milch", "REWE", variant, new BigDecimal("1.000"), "l");
        Receipt targetReceipt = new Receipt(900002, "mock raw text");
        targetReceipt.setStoreName("REWE");
        ReceiptItem target = new ReceiptItem(0, "BIO-MILCH", new BigDecimal("1.49"));
        target.updateParsedValues(new BigDecimal("1.000"), "l", null, null);
        targetReceipt.addItem(target);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());
        when(receiptItemRepository.findAll()).thenReturn(List.of(historicalOne, historicalTwo, historicalThree));

        productAssignmentService().assignItems(targetReceipt, List.of(target));

        assertThat(target.getProductFamily()).isSameAs(family);
        assertThat(target.getProductVariant()).isSameAs(variant);
        assertThat(target.getProductAssignmentSource()).isEqualTo(ProductAssignmentSource.HISTORY);
        assertThat(target.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.AUTO_ASSIGNED);
    }

    // Verifies product history does not merge otherwise identical descriptions with incompatible known sizes.
    @Test
    void incompatibleKnownSizeDoesNotUseTrustedHistoryVariant() {
        ProductFamily family = new ProductFamily("Bio Milch", null);
        ProductVariant variant = variant(family, "Bio Milch 1 l", new BigDecimal("1.000"), "l");
        List<ReceiptItem> historicalItems = List.of(
                trustedItem("Bio Milch", "REWE", variant, new BigDecimal("1.000"), "l"),
                trustedItem("Bio Milch", "REWE", variant, new BigDecimal("1.000"), "l"),
                trustedItem("Bio Milch", "REWE", variant, new BigDecimal("1.000"), "l"));
        Receipt targetReceipt = new Receipt(900003, "mock raw text");
        targetReceipt.setStoreName("REWE");
        ReceiptItem target = new ReceiptItem(0, "Bio Milch", new BigDecimal("0.99"));
        target.updateParsedValues(new BigDecimal("500"), "ml", null, null);
        targetReceipt.addItem(target);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());
        when(receiptItemRepository.findAll()).thenReturn(historicalItems);
        when(aiProductAssignmentClient.isAvailable()).thenReturn(false);

        productAssignmentService().assignItems(targetReceipt, List.of(target));

        assertThat(target.getProductFamily()).isNull();
        assertThat(target.getProductVariant()).isNull();
        assertThat(target.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.NEEDS_REVIEW);
    }

    // Verifies a high-confidence AI response can assign an existing product while exposing only item-level data to the client.
    @Test
    void highConfidenceAiResponseAssignsExistingFamilyAndVariant() {
        ProductFamily family = new ProductFamily("Haferdrink", null);
        ProductVariant variant = variant(family, "Haferdrink 1 l", BigDecimal.ONE, "l");
        Receipt receipt = new Receipt(900004, "private receipt text must not be sent");
        receipt.setStoreName("dm");
        ReceiptItem item = new ReceiptItem(0, "Hafer Drink", new BigDecimal("1.79"));
        item.updateParsedValues(BigDecimal.ONE, "l", new BigDecimal("1.79"), null);
        receipt.addItem(item);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());
        when(receiptItemRepository.findAll()).thenReturn(List.of());
        when(aiProductAssignmentClient.isAvailable()).thenReturn(true);
        when(productFamilyRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(family));
        when(productVariantRepository.findByActiveTrueOrderByProductFamily_NameAscNameAsc()).thenReturn(List.of(variant));
        when(productFamilyRepository.findById(7L)).thenReturn(Optional.of(family));
        when(productVariantRepository.findById(8L)).thenReturn(Optional.of(variant));
        when(aiProductAssignmentClient.assign(any())).thenReturn(
                new AiProductAssignmentResponse(7L, 8L, new BigDecimal("0.950"), "mock-product-model"));

        productAssignmentService().assignItems(receipt, List.of(item));

        assertThat(item.getProductFamily()).isSameAs(family);
        assertThat(item.getProductVariant()).isSameAs(variant);
        assertThat(item.getProductAssignmentSource()).isEqualTo(ProductAssignmentSource.AI);
        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.AUTO_ASSIGNED);
        verify(productAssignmentLogRepository).save(any());
    }

    // Verifies an uncertain AI result is retained only as an auditable review signal, never as an automatic assignment.
    @Test
    void lowConfidenceAiResponseLeavesItemForReview() {
        ProductFamily family = new ProductFamily("Haferdrink", null);
        ProductVariant variant = variant(family, "Haferdrink 1 l", BigDecimal.ONE, "l");
        Receipt receipt = new Receipt(900005, "mock raw text");
        receipt.setStoreName("dm");
        ReceiptItem item = new ReceiptItem(0, "Hafer Drink", new BigDecimal("1.79"));
        receipt.addItem(item);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());
        when(receiptItemRepository.findAll()).thenReturn(List.of());
        when(aiProductAssignmentClient.isAvailable()).thenReturn(true);
        when(productFamilyRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(family));
        when(productVariantRepository.findByActiveTrueOrderByProductFamily_NameAscNameAsc()).thenReturn(List.of(variant));
        when(productFamilyRepository.findById(7L)).thenReturn(Optional.of(family));
        when(productVariantRepository.findById(8L)).thenReturn(Optional.of(variant));
        when(aiProductAssignmentClient.assign(any())).thenReturn(
                new AiProductAssignmentResponse(7L, 8L, new BigDecimal("0.740"), "mock-product-model"));

        productAssignmentService().assignItems(receipt, List.of(item));

        assertThat(item.getProductFamily()).isNull();
        assertThat(item.getProductVariant()).isNull();
        assertThat(item.getProductAssignmentSource()).isNull();
        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.NEEDS_REVIEW);
        assertThat(item.getProductAssignmentConfidence()).isEqualByComparingTo("0.740");
        verify(productAssignmentLogRepository).save(any());
    }

    // Verifies AI receives a family-only candidate when no safe variant has been created yet.
    @Test
    void aiReceivesFamilyOnlyCandidateWhenNoVariantExists() {
        ProductFamily family = new ProductFamily("Unbekannter Haferdrink", null);
        ReflectionTestUtils.setField(family, "id", 7L);
        Receipt receipt = new Receipt(900051, "mock raw text");
        receipt.setStoreName("dm");
        ReceiptItem item = new ReceiptItem(0, "Haferdrink Neu", new BigDecimal("1.79"));
        receipt.addItem(item);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());
        when(receiptItemRepository.findAll()).thenReturn(List.of());
        when(aiProductAssignmentClient.isAvailable()).thenReturn(true);
        when(productFamilyRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(family));
        when(productVariantRepository.findByActiveTrueOrderByProductFamily_NameAscNameAsc()).thenReturn(List.of());
        when(productFamilyRepository.findById(7L)).thenReturn(Optional.of(family));
        when(aiProductAssignmentClient.assign(any())).thenReturn(
                new AiProductAssignmentResponse(7L, null, new BigDecimal("0.950"), "mock-product-model"));

        productAssignmentService().assignItems(receipt, List.of(item));

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(AiProductAssignmentRequest.class);
        verify(aiProductAssignmentClient).assign(requestCaptor.capture());
        assertThat(requestCaptor.getValue().candidates())
                .contains(new AiProductCandidate(7L, "Unbekannter Haferdrink", null, null));
        assertThat(item.getProductFamily()).isSameAs(family);
        assertThat(item.getProductVariant()).isNull();
    }

    // Verifies a failed AI attempt is auditable and leaves the item in review instead of silently dropping the failure.
    @Test
    void failedAiAttemptCreatesReviewAuditEntry() {
        ProductFamily family = new ProductFamily("Haferdrink", null);
        ReflectionTestUtils.setField(family, "id", 7L);
        Receipt receipt = new Receipt(900052, "private receipt text");
        receipt.setStoreName("dm");
        ReceiptItem item = new ReceiptItem(0, "Hafer Drink", new BigDecimal("1.79"));
        receipt.addItem(item);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());
        when(receiptItemRepository.findAll()).thenReturn(List.of());
        when(aiProductAssignmentClient.isAvailable()).thenReturn(true);
        when(productFamilyRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(family));
        when(productVariantRepository.findByActiveTrueOrderByProductFamily_NameAscNameAsc()).thenReturn(List.of());
        doThrow(new IllegalStateException("mock transport failure"))
                .when(aiProductAssignmentClient).assign(any());

        productAssignmentService().assignItems(receipt, List.of(item));

        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.NEEDS_REVIEW);
        verify(productAssignmentLogRepository).save(any());
    }

    // Verifies discounts are not forced into a fake product family and receive the explicit NO_PRODUCT status.
    @Test
    void discountLineIsMarkedNoProduct() {
        Receipt receipt = new Receipt(900006, "mock raw text");
        ReceiptItem item = new ReceiptItem(0, "Coupon Rabatt", new BigDecimal("-1.00"));
        receipt.addItem(item);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());

        productAssignmentService().assignItems(receipt, List.of(item));

        assertThat(item.getProductFamily()).isNull();
        assertThat(item.getProductVariant()).isNull();
        assertThat(item.getProductAssignmentSource()).isNull();
        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.NO_PRODUCT);
    }

    // Verifies accounting lines from retailer receipts do not become product assignments.
    @ParameterizedTest
    @ValueSource(strings = {"Sofortstorno - Abteibrot", "PFAND 0,25 EURO", "CC gratis"})
    void accountingLineIsMarkedNoProduct(String description) {
        Receipt receipt = new Receipt(900010 + description.hashCode(), "mock raw text");
        ReceiptItem item = new ReceiptItem(0, description, new BigDecimal("-0.25"));
        receipt.addItem(item);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());

        productAssignmentService().assignItems(receipt, List.of(item));

        assertThat(item.getProductFamily()).isNull();
        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.NO_PRODUCT);
    }

    // Verifies a newly added rule resolves an item that was previously held for product review.
    @Test
    void newlyAddedRuleResolvesExistingReviewItem() {
        ProductFamily family = new ProductFamily("Test Wasser", null);
        ProductRule rule = new ProductRule(family, null, "REWE", RuleMatchType.EXACT, "TEST WASSER", 100);
        Receipt receipt = new Receipt(900011, "mock raw text");
        receipt.setStoreName("REWE");
        ReceiptItem item = new ReceiptItem(0, "TEST WASSER", new BigDecimal("1.00"));
        receipt.addItem(item);
        item.markProductNeedsReview(null);
        when(receiptItemRepository.findAll()).thenReturn(List.of(item));

        int changedItems = productAssignmentService().applyRuleToExistingItems(rule);

        assertThat(changedItems).isEqualTo(1);
        assertThat(item.getProductFamily()).isSameAs(family);
        assertThat(item.getProductAssignmentSource()).isEqualTo(ProductAssignmentSource.RULE);
        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.AUTO_ASSIGNED);
    }

    // Verifies a new rule cannot overwrite a product assignment confirmed by the user.
    @Test
    void newlyAddedRuleDoesNotOverwriteManualProductAssignment() {
        ProductFamily manualFamily = new ProductFamily("Manual Wasser", null);
        ProductFamily ruleFamily = new ProductFamily("Rule Wasser", null);
        ProductRule rule = new ProductRule(ruleFamily, null, "REWE", RuleMatchType.EXACT, "TEST WASSER", 100);
        Receipt receipt = new Receipt(900012, "mock raw text");
        receipt.setStoreName("REWE");
        ReceiptItem item = new ReceiptItem(0, "TEST WASSER", new BigDecimal("1.00"));
        receipt.addItem(item);
        item.assignProduct(manualFamily, null, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED, null);
        when(receiptItemRepository.findAll()).thenReturn(List.of(item));

        int changedItems = productAssignmentService().applyRuleToExistingItems(rule);

        assertThat(changedItems).isZero();
        assertThat(item.getProductFamily()).isSameAs(manualFamily);
        assertThat(item.getProductAssignmentSource()).isEqualTo(ProductAssignmentSource.MANUAL);
        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.CONFIRMED);
    }

    // Verifies a product family default category fills an empty category but never replaces a manual user decision.
    @Test
    void defaultCategoryOnlyFillsEmptyNonManualCategory() {
        Category defaultCategory = new Category("Getraenke", "#123456", "glass-water", 1);
        Category manualCategory = new Category("Drogerie", "#654321", "spray-can", 2);
        ProductFamily family = new ProductFamily("Mineralwasser", defaultCategory);
        ProductRule rule = new ProductRule(family, null, null, RuleMatchType.CONTAINS, "Wasser", 10);
        Receipt receipt = new Receipt(900007, "mock raw text");
        ReceiptItem emptyCategory = new ReceiptItem(0, "Mineral Wasser", new BigDecimal("0.79"));
        ReceiptItem manualCategoryItem = new ReceiptItem(1, "Mineral Wasser", new BigDecimal("0.79"));
        manualCategoryItem.assignCategory(manualCategory, CategorySource.MANUAL);
        receipt.addItem(emptyCategory);
        receipt.addItem(manualCategoryItem);
        when(productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(rule));

        productAssignmentService().assignItems(receipt, List.of(emptyCategory, manualCategoryItem));

        assertThat(emptyCategory.getCategory()).isSameAs(defaultCategory);
        assertThat(emptyCategory.getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(manualCategoryItem.getCategory()).isSameAs(manualCategory);
        assertThat(manualCategoryItem.getCategorySource()).isEqualTo(CategorySource.MANUAL);
    }

    private ProductAssignmentService productAssignmentService() {
        return new ProductAssignmentService(
                productRuleRepository,
                productFamilyRepository,
                productVariantRepository,
                receiptItemRepository,
                productAssignmentLogRepository,
                appSettingRepository,
                new ProductRuleMatcher(),
                new ProductUnitNormalizer(),
                aiProductAssignmentClient);
    }

    private static ProductVariant variant(ProductFamily family, String name, BigDecimal quantity, String unit) {
        return new ProductVariant(family, name, quantity, unit, 1, null, quantity, unit, null);
    }

    private static ReceiptItem trustedItem(
            String description,
            String storeName,
            ProductVariant variant,
            BigDecimal quantity,
            String unit) {
        Receipt receipt = new Receipt(900100 + description.hashCode() + quantity.intValue(), "mock raw text");
        receipt.setStoreName(storeName);
        ReceiptItem item = new ReceiptItem(0, description, new BigDecimal("1.00"));
        item.updateParsedValues(quantity, unit, null, null);
        receipt.addItem(item);
        item.assignProduct(
                variant.getProductFamily(),
                variant,
                ProductAssignmentSource.RULE,
                ProductAssignmentStatus.AUTO_ASSIGNED,
                null);
        return item;
    }
}
