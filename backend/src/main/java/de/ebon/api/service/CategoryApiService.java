package de.ebon.api.service;

import de.ebon.api.dto.CategoryDto;
import de.ebon.api.dto.CategoryPatchRequest;
import de.ebon.api.dto.CategoryRequest;
import de.ebon.categorization.CategoryDeletionResult;
import de.ebon.categorization.CategoryManagementService;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryApiService {

    private final CategoryRepository categoryRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final CategoryManagementService categoryManagementService;

    public CategoryApiService(
            CategoryRepository categoryRepository,
            ReceiptItemRepository receiptItemRepository,
            CategoryManagementService categoryManagementService) {
        this.categoryRepository = categoryRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.categoryManagementService = categoryManagementService;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> list(boolean includeInactive) {
        List<Category> categories = includeInactive
                ? categoryRepository.findAllByOrderBySortOrderAscNameAsc()
                : categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        return categories.stream().map(this::toDto).toList();
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        requireUniqueName(request.name(), null);
        Category category = new Category(
                request.name().trim(),
                request.colorHex(),
                request.icon(),
                request.sortOrder() == null ? 0 : request.sortOrder());
        if (Boolean.FALSE.equals(request.isActive())) {
            category.deactivate();
        }
        return toDto(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = category(id);
        requireUniqueName(request.name(), id);
        category.update(
                request.name().trim(),
                request.colorHex(),
                request.icon(),
                request.sortOrder(),
                request.isActive());
        return toDto(category);
    }

    @Transactional
    public CategoryDto patch(Long id, CategoryPatchRequest request) {
        Category category = category(id);
        if (request.name() != null) {
            requireUniqueName(request.name(), id);
        }
        category.update(
                request.name() == null ? null : request.name().trim(),
                request.colorHex(),
                request.icon(),
                request.sortOrder(),
                request.isActive());
        return toDto(category);
    }

    @Transactional
    public CategoryDeletionResult deleteOrDeactivate(Long id) {
        return categoryManagementService.deleteOrDeactivateCategory(id);
    }

    private Category category(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Kategorie nicht gefunden."));
    }

    private void requireUniqueName(String name, Long currentId) {
        categoryRepository.findByNameIgnoreCase(name.trim())
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Kategoriename existiert bereits.");
                });
    }

    public CategoryDto toDto(Category category) {
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getColorHex(),
                category.getIcon(),
                category.isActive(),
                category.getSortOrder(),
                receiptItemRepository.countByCategory_Id(category.getId()));
    }
}
