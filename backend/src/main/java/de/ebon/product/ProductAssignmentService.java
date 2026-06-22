package de.ebon.product;

import de.ebon.persistence.model.AppSetting;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.ProductAssignmentLog;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductRule;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.AppSettingRepository;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductRuleRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductAssignmentService {

    private static final String HISTORY_MIN_MATCHES_SETTING = "product_history_min_confirmed_matches";
    private static final String HISTORY_MIN_VARIANT_SHARE_SETTING = "product_history_min_variant_share";
    private static final String AI_MIN_CONFIDENCE_SETTING = "ai_categorization_min_confidence";
    private static final int DEFAULT_HISTORY_MIN_MATCHES = 3;
    private static final BigDecimal DEFAULT_HISTORY_MIN_VARIANT_SHARE = new BigDecimal("0.900");
    private static final BigDecimal DEFAULT_AI_MIN_CONFIDENCE = new BigDecimal("0.900");
    private static final Pattern NON_PRODUCT_LINE = Pattern.compile(
            "(?i)\\b(rabatt|coupon|gutschein|zahlung|ec[- ]?karte|kreditkarte|visa|mastercard|barzahlung|rundung|wechselgeld|summe|mwst|steuer)\\b");

    private final ProductRuleRepository productRuleRepository;
    private final ProductFamilyRepository productFamilyRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final ProductAssignmentLogRepository productAssignmentLogRepository;
    private final AppSettingRepository appSettingRepository;
    private final ProductRuleMatcher productRuleMatcher;
    private final ProductUnitNormalizer productUnitNormalizer;
    private final AiProductAssignmentClient aiProductAssignmentClient;

    public ProductAssignmentService(
            ProductRuleRepository productRuleRepository,
            ProductFamilyRepository productFamilyRepository,
            ProductVariantRepository productVariantRepository,
            ReceiptItemRepository receiptItemRepository,
            ProductAssignmentLogRepository productAssignmentLogRepository,
            AppSettingRepository appSettingRepository,
            ProductRuleMatcher productRuleMatcher,
            ProductUnitNormalizer productUnitNormalizer,
            AiProductAssignmentClient aiProductAssignmentClient) {
        this.productRuleRepository = productRuleRepository;
        this.productFamilyRepository = productFamilyRepository;
        this.productVariantRepository = productVariantRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.productAssignmentLogRepository = productAssignmentLogRepository;
        this.appSettingRepository = appSettingRepository;
        this.productRuleMatcher = productRuleMatcher;
        this.productUnitNormalizer = productUnitNormalizer;
        this.aiProductAssignmentClient = aiProductAssignmentClient;
    }

    @Transactional
    public int assignItems(Receipt receipt, List<ReceiptItem> items) {
        List<ProductRule> activeRules = productRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc();
        int changed = 0;
        for (ReceiptItem item : items) {
            if (isProtected(item)) {
                continue;
            }
            if (isNonProductLine(item)) {
                item.markNoProduct();
                changed++;
                continue;
            }
            Optional<ProductRule> matchingRule = findMatchingRule(activeRules, item);
            if (matchingRule.isPresent()) {
                applyAssignment(
                        item,
                        matchingRule.get().getProductFamily(),
                        matchingRule.get().getProductVariant(),
                        ProductAssignmentSource.RULE,
                        ProductAssignmentStatus.AUTO_ASSIGNED,
                        null,
                        null,
                        "PRODUCT_RULE");
                changed++;
                continue;
            }
            Optional<ProductVariant> historyVariant = findClearHistory(item);
            if (historyVariant.isPresent()) {
                ProductVariant variant = historyVariant.get();
                applyAssignment(
                        item,
                        variant.getProductFamily(),
                        variant,
                        ProductAssignmentSource.HISTORY,
                        ProductAssignmentStatus.AUTO_ASSIGNED,
                        null,
                        null,
                        "TRUSTED_HISTORY");
                changed++;
                continue;
            }
            if (assignWithAi(item)) {
                changed++;
                continue;
            }
            item.markProductNeedsReview(null);
            changed++;
        }
        return changed;
    }

    @Transactional
    public int assignReceipt(Long receiptId) {
        List<ReceiptItem> items = receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receiptId);
        if (items.isEmpty()) {
            return 0;
        }
        return assignItems(items.getFirst().getReceipt(), items);
    }

    @Transactional
    public int assignOpenItems() {
        return receiptItemRepository.findAll().stream()
                .filter(item -> item.getReceipt() != null && item.getReceipt().getDeletedAt() == null)
                .collect(Collectors.groupingBy(ReceiptItem::getReceipt))
                .entrySet().stream()
                .mapToInt(entry -> assignItems(entry.getKey(), entry.getValue()))
                .sum();
    }

    @Transactional
    public int applyRuleToExistingItems(ProductRule rule) {
        if (!rule.isActive()) {
            return 0;
        }
        int changed = 0;
        for (ReceiptItem item : receiptItemRepository.findAll()) {
            if (isProtected(item) || !productRuleMatcher.matches(rule, item)) {
                continue;
            }
            applyAssignment(
                    item,
                    rule.getProductFamily(),
                    rule.getProductVariant(),
                    ProductAssignmentSource.RULE,
                    ProductAssignmentStatus.AUTO_ASSIGNED,
                    null,
                    null,
                    "PRODUCT_RULE_BULK_APPLY");
            changed++;
        }
        return changed;
    }

    private boolean isProtected(ReceiptItem item) {
        return item.getProductAssignmentStatus() == ProductAssignmentStatus.NO_PRODUCT
                || item.getProductAssignmentSource() == ProductAssignmentSource.MANUAL
                || item.getProductAssignmentStatus() == ProductAssignmentStatus.CONFIRMED;
    }

    private boolean isNonProductLine(ReceiptItem item) {
        return item.getDescription() != null && NON_PRODUCT_LINE.matcher(item.getDescription()).find();
    }

    private Optional<ProductRule> findMatchingRule(List<ProductRule> rules, ReceiptItem item) {
        return rules.stream()
                .filter(rule -> rule.getProductFamily().isActive())
                .filter(rule -> rule.getProductVariant() == null || rule.getProductVariant().isActive())
                .filter(rule -> productRuleMatcher.matches(rule, item))
                .sorted(Comparator
                        .comparing((ProductRule rule) -> productRuleMatcher.isStoreSpecific(rule) ? 0 : 1)
                        .thenComparingInt(ProductRule::getPriority)
                        .thenComparing(ProductRule::getId, Comparator.nullsLast(Long::compareTo)))
                .findFirst();
    }

    private Optional<ProductVariant> findClearHistory(ReceiptItem target) {
        List<ReceiptItem> trustedMatches = receiptItemRepository.findAll().stream()
                .filter(item -> item.getId() == null || !item.getId().equals(target.getId()))
                .filter(this::isTrustedHistory)
                .filter(item -> sameHistoryContext(item, target))
                .filter(item -> compatibleObservedSize(item, target))
                .filter(item -> item.getProductVariant().isActive())
                .toList();
        if (trustedMatches.isEmpty()) {
            return Optional.empty();
        }

        Map<ProductVariant, Long> counts = trustedMatches.stream()
                .collect(Collectors.groupingBy(ReceiptItem::getProductVariant, Collectors.counting()));
        Map.Entry<ProductVariant, Long> best = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        int minimumMatches = positiveIntegerSetting(HISTORY_MIN_MATCHES_SETTING, DEFAULT_HISTORY_MIN_MATCHES);
        BigDecimal minimumShare = confidenceSetting(HISTORY_MIN_VARIANT_SHARE_SETTING, DEFAULT_HISTORY_MIN_VARIANT_SHARE);
        BigDecimal share = BigDecimal.valueOf(best.getValue())
                .divide(BigDecimal.valueOf(trustedMatches.size()), 3, RoundingMode.HALF_UP);
        return best.getValue() >= minimumMatches && share.compareTo(minimumShare) >= 0
                ? Optional.of(best.getKey())
                : Optional.empty();
    }

    private boolean isTrustedHistory(ReceiptItem item) {
        return item.getProductVariant() != null
                && (item.getProductAssignmentSource() == ProductAssignmentSource.MANUAL
                        || item.getProductAssignmentSource() == ProductAssignmentSource.RULE)
                && (item.getProductAssignmentStatus() == ProductAssignmentStatus.CONFIRMED
                        || item.getProductAssignmentStatus() == ProductAssignmentStatus.AUTO_ASSIGNED);
    }

    private boolean sameHistoryContext(ReceiptItem first, ReceiptItem second) {
        String firstStore = first.getReceipt() == null ? null : first.getReceipt().getStoreName();
        String secondStore = second.getReceipt() == null ? null : second.getReceipt().getStoreName();
        return productRuleMatcher.compact(first.getDescription()).equals(productRuleMatcher.compact(second.getDescription()))
                && productRuleMatcher.compact(firstStore).equals(productRuleMatcher.compact(secondStore));
    }

    private boolean compatibleObservedSize(ReceiptItem historical, ReceiptItem target) {
        Optional<ProductUnitNormalizer.NormalizedQuantity> targetQuantity = productUnitNormalizer.normalize(
                target.getQuantity(), target.getUnit());
        if (targetQuantity.isEmpty()) {
            return true;
        }
        Optional<ProductUnitNormalizer.NormalizedQuantity> historicalQuantity = productUnitNormalizer.normalize(
                historical.getQuantity(), historical.getUnit());
        return historicalQuantity.isPresent()
                && historicalQuantity.get().unit().equals(targetQuantity.get().unit())
                && historicalQuantity.get().quantity().compareTo(targetQuantity.get().quantity()) == 0;
    }

    private boolean assignWithAi(ReceiptItem item) {
        if (!aiProductAssignmentClient.isAvailable()) {
            return false;
        }
        try {
            AiProductAssignmentResponse response = aiProductAssignmentClient.assign(new AiProductAssignmentRequest(
                    item.getId(),
                    item.getDescription(),
                    item.getReceipt() == null ? null : item.getReceipt().getStoreName(),
                    item.getTotalPrice(),
                    item.getQuantity(),
                    item.getUnit(),
                    activeCandidates(),
                    confidenceSetting(AI_MIN_CONFIDENCE_SETTING, DEFAULT_AI_MIN_CONFIDENCE)));
            if (response == null) {
                markAiNeedsReview(item, null, null, null, null, "EMPTY_AI_RESPONSE");
                return true;
            }
            Optional<ProductFamily> family = response.productFamilyId() == null
                    ? Optional.empty()
                    : productFamilyRepository.findById(response.productFamilyId()).filter(ProductFamily::isActive);
            Optional<ProductVariant> variant = response.productVariantId() == null
                    ? Optional.empty()
                    : productVariantRepository.findById(response.productVariantId()).filter(ProductVariant::isActive);
            boolean validVariant = variant.isEmpty() || family.isPresent() && variant.get().getProductFamily() == family.get();
            boolean accepted = family.isPresent()
                    && validVariant
                    && response.confidence() != null
                    && response.confidence().compareTo(confidenceSetting(AI_MIN_CONFIDENCE_SETTING, DEFAULT_AI_MIN_CONFIDENCE)) >= 0;
            if (accepted) {
                applyAssignment(
                        item,
                        family.get(),
                        variant.orElse(null),
                        ProductAssignmentSource.AI,
                        ProductAssignmentStatus.AUTO_ASSIGNED,
                        response.confidence(),
                        response.modelUsed(),
                        "HIGH_CONFIDENCE_AI");
            } else {
                markAiNeedsReview(
                        item,
                        family.orElse(null),
                        validVariant ? variant.orElse(null) : null,
                        response.confidence(),
                        response.modelUsed(),
                        validVariant ? "LOW_CONFIDENCE_OR_UNKNOWN_PRODUCT" : "VARIANT_FAMILY_MISMATCH");
            }
            return true;
        } catch (RuntimeException exception) {
            markAiNeedsReview(item, null, null, null, null, "AI_CLIENT_FAILURE");
            return true;
        }
    }

    private void markAiNeedsReview(
            ReceiptItem item,
            ProductFamily family,
            ProductVariant variant,
            BigDecimal confidence,
            String modelUsed,
            String reason) {
        item.markProductNeedsReview(confidence);
        productAssignmentLogRepository.save(new ProductAssignmentLog(
                item,
                family,
                variant,
                ProductAssignmentSource.AI,
                ProductAssignmentStatus.NEEDS_REVIEW,
                confidence,
                safeModel(modelUsed),
                reason));
    }

    private List<AiProductCandidate> activeCandidates() {
        Map<Long, ProductFamily> families = productFamilyRepository.findByActiveTrueOrderByNameAsc().stream()
                .collect(Collectors.toMap(ProductFamily::getId, Function.identity()));
        Stream<AiProductCandidate> familyCandidates = families.values().stream()
                .map(family -> new AiProductCandidate(
                        family.getId(),
                        family.getName(),
                        null,
                        null));
        Stream<AiProductCandidate> variantCandidates = productVariantRepository.findByActiveTrueOrderByProductFamily_NameAscNameAsc().stream()
                .filter(variant -> families.containsKey(variant.getProductFamily().getId()))
                .map(variant -> new AiProductCandidate(
                        variant.getProductFamily().getId(),
                        variant.getProductFamily().getName(),
                        variant.getId(),
                        variant.getName()));
        return Stream.concat(familyCandidates, variantCandidates).toList();
    }

    private void applyAssignment(
            ReceiptItem item,
            ProductFamily family,
            ProductVariant variant,
            ProductAssignmentSource source,
            ProductAssignmentStatus status,
            BigDecimal confidence,
            String modelUsed,
            String reason) {
        item.assignProduct(family, variant, source, status, confidence);
        applyDefaultCategory(item, family);
        productAssignmentLogRepository.save(new ProductAssignmentLog(
                item,
                family,
                variant,
                source,
                status,
                confidence,
                safeModel(modelUsed),
                reason));
    }

    private void applyDefaultCategory(ReceiptItem item, ProductFamily family) {
        Category defaultCategory = family.getDefaultCategory();
        if (!item.isManuallyEdited()
                && item.getCategory() == null
                && defaultCategory != null
                && defaultCategory.isActive()) {
            item.assignCategory(defaultCategory, CategorySource.RULE);
        }
    }

    private int positiveIntegerSetting(String key, int fallback) {
        try {
            int value = appSettingRepository.findById(key).map(AppSetting::getValue).map(String::trim).map(Integer::parseInt).orElse(fallback);
            return value >= 1 ? value : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private BigDecimal confidenceSetting(String key, BigDecimal fallback) {
        try {
            BigDecimal value = appSettingRepository.findById(key)
                    .map(AppSetting::getValue)
                    .map(String::trim)
                    .map(BigDecimal::new)
                    .orElse(fallback);
            return value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.ONE) <= 0 ? value : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String safeModel(String model) {
        return model == null || model.isBlank() ? "unknown" : model.trim();
    }
}
