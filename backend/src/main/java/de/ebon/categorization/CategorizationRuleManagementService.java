package de.ebon.categorization;

import de.ebon.api.dto.CategorizationRuleDto;
import de.ebon.api.dto.CategorizationRulePreviewRequest;
import de.ebon.api.dto.CategorizationRuleRequest;
import de.ebon.persistence.model.CategorizationRule;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.CategorizationRuleRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategorizationRuleManagementService {

    private final CategorizationRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final CategorizationRuleMatcher ruleMatcher;
    private final CategorizationService categorizationService;

    public CategorizationRuleManagementService(
            CategorizationRuleRepository ruleRepository,
            CategoryRepository categoryRepository,
            ReceiptItemRepository receiptItemRepository,
            CategorizationRuleMatcher ruleMatcher,
            CategorizationService categorizationService) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.ruleMatcher = ruleMatcher;
        this.categorizationService = categorizationService;
    }

    @Transactional(readOnly = true)
    public List<CategorizationRuleDto> list() {
        return ruleRepository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CategorizationRuleDto create(CategorizationRuleRequest request) {
        Category category = activeCategory(request.categoryId());
        CategorizationRule rule = new CategorizationRule(
                category,
                request.matchField(),
                request.matchType(),
                request.matchValue().trim(),
                request.priority() == null ? 100 : request.priority());
        if (Boolean.FALSE.equals(request.isActive())) {
            rule.deactivate();
        }
        CategorizationRule saved = ruleRepository.saveAndFlush(rule);
        if (Boolean.TRUE.equals(request.applyToExisting())) {
            categorizationService.applyRuleToExistingItems(saved.getId());
        }
        return toDto(saved);
    }

    @Transactional
    public CategorizationRuleDto update(Long id, CategorizationRuleRequest request) {
        CategorizationRule rule = rule(id);
        rule.update(
                activeCategory(request.categoryId()),
                request.matchField(),
                request.matchType(),
                request.matchValue().trim(),
                request.priority() == null ? 100 : request.priority(),
                request.isActive());
        if (Boolean.TRUE.equals(request.applyToExisting())) {
            categorizationService.applyRuleToExistingItems(rule.getId());
        }
        return toDto(rule);
    }

    @Transactional
    public void delete(Long id) {
        ruleRepository.delete(rule(id));
    }

    @Transactional
    public int apply(Long id) {
        return categorizationService.applyRuleToExistingItems(id);
    }

    @Transactional(readOnly = true)
    public long preview(CategorizationRulePreviewRequest request) {
        Category category = request.categoryId() == null
                ? firstActiveCategory()
                : activeCategory(request.categoryId());
        CategorizationRule transientRule = new CategorizationRule(
                category,
                request.matchField(),
                request.matchType(),
                request.matchValue().trim(),
                100);
        return receiptItemRepository.findAll().stream()
                .filter(item -> !item.isManuallyEdited())
                .filter(item -> item.getCategory() == null || item.getCategorySource() == de.ebon.persistence.model.CategorySource.AI)
                .filter(item -> ruleMatcher.matches(transientRule, item))
                .count();
    }

    private Category activeCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Kategorie nicht gefunden."));
        if (!category.isActive()) {
            throw new IllegalArgumentException("Kategorie ist deaktiviert.");
        }
        return category;
    }

    private Category firstActiveCategory() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Keine aktive Kategorie vorhanden."));
    }

    private CategorizationRule rule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Kategorisierungsregel nicht gefunden."));
    }

    private CategorizationRuleDto toDto(CategorizationRule rule) {
        return new CategorizationRuleDto(
                rule.getId(),
                rule.getCategory().getId(),
                rule.getCategory().getName(),
                rule.getMatchField(),
                rule.getMatchType(),
                rule.getMatchValue(),
                rule.getPriority(),
                rule.isActive(),
                rule.getCreatedAt());
    }
}
