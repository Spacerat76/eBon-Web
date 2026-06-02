package de.ebon.categorization;

import de.ebon.persistence.model.Category;
import de.ebon.persistence.repository.CategorizationRuleRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryManagementService {

    private final CategoryRepository categoryRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final CategorizationRuleRepository categorizationRuleRepository;

    public CategoryManagementService(
            CategoryRepository categoryRepository,
            ReceiptItemRepository receiptItemRepository,
            CategorizationRuleRepository categorizationRuleRepository) {
        this.categoryRepository = categoryRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.categorizationRuleRepository = categorizationRuleRepository;
    }

    @Transactional
    public CategoryDeletionResult deleteOrDeactivateCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Kategorie nicht gefunden."));

        if (receiptItemRepository.existsByCategory_Id(categoryId)
                || categorizationRuleRepository.existsByCategory_Id(categoryId)) {
            category.deactivate();
            return CategoryDeletionResult.DEACTIVATED;
        }

        categoryRepository.delete(category);
        return CategoryDeletionResult.HARD_DELETED;
    }
}
