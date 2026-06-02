package de.ebon.categorization;

import de.ebon.persistence.model.AiCategorizationLog;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.CategorizationRule;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.AiCategorizationLogRepository;
import de.ebon.persistence.repository.CategorizationRuleRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategorizationService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationRuleRepository categorizationRuleRepository;
    private final AiCategorizationLogRepository aiCategorizationLogRepository;
    private final CategorizationRuleMatcher ruleMatcher;
    private final AiCategorizationClient aiCategorizationClient;

    public CategorizationService(
            ReceiptRepository receiptRepository,
            ReceiptItemRepository receiptItemRepository,
            CategoryRepository categoryRepository,
            CategorizationRuleRepository categorizationRuleRepository,
            AiCategorizationLogRepository aiCategorizationLogRepository,
            CategorizationRuleMatcher ruleMatcher,
            AiCategorizationClient aiCategorizationClient) {
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationRuleRepository = categorizationRuleRepository;
        this.aiCategorizationLogRepository = aiCategorizationLogRepository;
        this.ruleMatcher = ruleMatcher;
        this.aiCategorizationClient = aiCategorizationClient;
    }

    @Transactional
    public void categorizeReceipt(Long receiptId) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new EntityNotFoundException("Bon nicht gefunden."));
        List<ReceiptItem> items = receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receiptId);
        categorizeItems(receipt, items);
    }

    @Transactional
    public void manuallyCategorizeItem(Long receiptItemId, Long categoryId) {
        ReceiptItem item = receiptItemRepository.findById(receiptItemId)
                .orElseThrow(() -> new EntityNotFoundException("Bon-Position nicht gefunden."));
        Category category = activeCategory(categoryId);
        item.assignCategory(category, CategorySource.MANUAL);
    }

    @Transactional
    public int applyRuleToExistingItems(Long ruleId) {
        CategorizationRule rule = categorizationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Kategorisierungsregel nicht gefunden."));
        if (!rule.isActive()) {
            return 0;
        }

        int changed = 0;
        for (ReceiptItem item : receiptItemRepository.findAll()) {
            if (!canBulkApplyRule(item) || !ruleMatcher.matches(rule, item)) {
                continue;
            }
            item.assignCategory(rule.getCategory(), CategorySource.RULE);
            changed++;
        }
        return changed;
    }

    private void categorizeItems(Receipt receipt, List<ReceiptItem> items) {
        List<CategorizationRule> activeRules = categorizationRuleRepository.findByActiveTrueOrderByPriorityAscIdAsc();
        for (ReceiptItem item : items) {
            if (item.isManuallyEdited()) {
                continue;
            }
            findMatchingRule(activeRules, item)
                    .ifPresent(rule -> item.assignCategory(rule.getCategory(), CategorySource.RULE));
        }

        List<ReceiptItem> uncategorizedItems = items.stream()
                .filter(item -> !item.isManuallyEdited())
                .filter(item -> item.getCategory() == null)
                .toList();
        categorizeWithAi(receipt, uncategorizedItems);
    }

    private Optional<CategorizationRule> findMatchingRule(List<CategorizationRule> rules, ReceiptItem item) {
        return rules.stream()
                .filter(rule -> rule.getCategory().isActive())
                .filter(rule -> ruleMatcher.matches(rule, item))
                .findFirst();
    }

    private void categorizeWithAi(Receipt receipt, List<ReceiptItem> items) {
        if (items.isEmpty() || !aiCategorizationClient.isAvailable()) {
            return;
        }

        List<Category> activeCategories = categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        if (activeCategories.isEmpty()) {
            return;
        }

        Map<String, Category> categoriesByNormalizedName = activeCategories.stream()
                .collect(Collectors.toMap(
                        category -> normalize(category.getName()),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        AiCategorizationBatchRequest request = new AiCategorizationBatchRequest(
                items.stream()
                        .map(item -> new AiCategorizationItem(item.getId(), item.getDescription(), receipt.getStoreName()))
                        .toList(),
                activeCategories.stream().map(Category::getName).toList());

        AiCategorizationBatchResponse response;
        try {
            response = aiCategorizationClient.categorize(request);
        } catch (RuntimeException exception) {
            return;
        }

        Map<Long, AiCategorizationSuggestion> suggestionsByItemId = response.suggestions().stream()
                .filter(suggestion -> suggestion.itemId() != null)
                .collect(Collectors.toMap(
                        AiCategorizationSuggestion::itemId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        for (ReceiptItem item : items) {
            AiCategorizationSuggestion suggestion = suggestionsByItemId.get(item.getId());
            if (suggestion == null) {
                continue;
            }
            Category assignedCategory = categoriesByNormalizedName.get(normalize(suggestion.categoryName()));
            aiCategorizationLogRepository.save(new AiCategorizationLog(
                    item,
                    safe(response.promptSent()),
                    safe(response.responseReceived()),
                    assignedCategory,
                    suggestion.confidence(),
                    safeModel(response.modelUsed())));
            if (assignedCategory != null) {
                item.assignCategory(assignedCategory, CategorySource.AI);
            }
        }
    }

    private boolean canBulkApplyRule(ReceiptItem item) {
        return !item.isManuallyEdited()
                && (item.getCategory() == null || item.getCategorySource() == CategorySource.AI);
    }

    private Category activeCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Kategorie nicht gefunden."));
        if (!category.isActive()) {
            throw new IllegalArgumentException("Kategorie ist deaktiviert.");
        }
        return category;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeModel(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
