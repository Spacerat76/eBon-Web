package de.ebon.product;

import de.ebon.api.dto.ProductChangePreviewDto;
import de.ebon.api.dto.ProductFamilyMergeApplyRequest;
import de.ebon.api.dto.ProductFamilyMergeRequest;
import de.ebon.api.dto.ProductFamilySplitApplyRequest;
import de.ebon.api.dto.ProductFamilySplitRequest;
import de.ebon.api.dto.ProductFamilyRequest;
import de.ebon.api.dto.ProductVariantMergeApplyRequest;
import de.ebon.api.dto.ProductVariantMergeRequest;
import de.ebon.api.dto.ProductVariantRequest;
import de.ebon.api.dto.ProductVariantSplitApplyRequest;
import de.ebon.api.dto.ProductVariantSplitRequest;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.ProductAssignmentLog;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductRule;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductRuleRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductMaintenanceService {

    private final ProductFamilyRepository productFamilyRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRuleRepository productRuleRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final ProductAssignmentLogRepository productAssignmentLogRepository;
    private final CategoryRepository categoryRepository;

    public ProductMaintenanceService(
            ProductFamilyRepository productFamilyRepository,
            ProductVariantRepository productVariantRepository,
            ProductRuleRepository productRuleRepository,
            ReceiptItemRepository receiptItemRepository,
            ProductAssignmentLogRepository productAssignmentLogRepository,
            CategoryRepository categoryRepository) {
        this.productFamilyRepository = productFamilyRepository;
        this.productVariantRepository = productVariantRepository;
        this.productRuleRepository = productRuleRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.productAssignmentLogRepository = productAssignmentLogRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public ProductChangePreviewDto previewFamilyMerge(ProductFamilyMergeRequest request) {
        ProductFamily source = family(request.sourceFamilyId());
        ProductFamily target = family(request.targetFamilyId());
        requireDistinct(source, target);
        requireFamilyWithoutVariants(source);
        return preview(source, target, sourceItems(source));
    }

    @Transactional
    public ProductChangePreviewDto applyFamilyMerge(ProductFamilyMergeApplyRequest request) {
        ProductFamily source = family(request.sourceFamilyId());
        ProductFamily target = family(request.targetFamilyId());
        requireDistinct(source, target);
        requireFamilyWithoutVariants(source);
        List<ReceiptItem> items = sourceItems(source);
        ProductChangePreviewDto preview = preview(source, target, items);
        for (ReceiptItem item : items) {
            assignAndAudit(item, target, null, "FAMILY_MERGE");
        }
        for (ProductRule rule : productRuleRepository.findByProductFamily_Id(source.getId())) {
            rule.update(
                    target,
                    null,
                    rule.getStoreName(),
                    rule.getMatchType(),
                    rule.getMatchValue(),
                    rule.getPriority(),
                    rule.isActive());
        }
        source.deactivate();
        return preview;
    }

    @Transactional(readOnly = true)
    public ProductChangePreviewDto previewVariantMerge(ProductVariantMergeRequest request) {
        ProductVariant source = variant(request.sourceVariantId());
        ProductVariant target = variant(request.targetVariantId());
        requireDistinct(source, target);
        requireSameFamily(source, target);
        return preview(source, target, sourceItems(source));
    }

    @Transactional
    public ProductChangePreviewDto applyVariantMerge(ProductVariantMergeApplyRequest request) {
        ProductVariant source = variant(request.sourceVariantId());
        ProductVariant target = variant(request.targetVariantId());
        requireDistinct(source, target);
        requireSameFamily(source, target);
        if (!target.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Zielvariante ist deaktiviert.");
        }
        List<ReceiptItem> items = sourceItems(source);
        ProductChangePreviewDto preview = preview(source, target, items);
        for (ReceiptItem item : items) {
            assignAndAudit(item, target.getProductFamily(), target, "VARIANT_MERGE");
        }
        for (ProductRule rule : productRuleRepository.findByProductVariant_Id(source.getId())) {
            rule.update(
                    target.getProductFamily(), target, rule.getStoreName(), rule.getMatchType(),
                    rule.getMatchValue(), rule.getPriority(), rule.isActive());
        }
        source.deactivate();
        return preview;
    }

    @Transactional(readOnly = true)
    public ProductChangePreviewDto previewFamilySplit(ProductFamilySplitRequest request) {
        ProductFamily source = family(request.sourceFamilyId());
        requireFamilyWithoutVariants(source);
        ensureNewFamilyNameAvailable(request.newFamily().name());
        return preview(source, new ProductFamily(request.newFamily().name(), null), selectedFamilyItems(source, request.receiptItemIds()));
    }

    @Transactional
    public ProductChangePreviewDto applyFamilySplit(ProductFamilySplitApplyRequest request) {
        ProductFamily source = family(request.sourceFamilyId());
        requireFamilyWithoutVariants(source);
        ensureNewFamilyNameAvailable(request.newFamily().name());
        if (Boolean.FALSE.equals(request.newFamily().isActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Neue Produktfamilie muss aktiv sein.");
        }
        List<ReceiptItem> items = selectedFamilyItems(source, request.receiptItemIds());
        ProductFamily target = productFamilyRepository.saveAndFlush(new ProductFamily(
                request.newFamily().name(), activeCategoryOrNull(request.newFamily().defaultCategoryId())));
        ProductChangePreviewDto preview = preview(source, target, items);
        for (ReceiptItem item : items) {
            assignAndAudit(item, target, null, "FAMILY_SPLIT");
        }
        return preview;
    }

    @Transactional(readOnly = true)
    public ProductChangePreviewDto previewVariantSplit(ProductVariantSplitRequest request) {
        ProductVariant source = variant(request.sourceVariantId());
        requireMatchingFamily(source, request.newVariant());
        return preview(source, new ProductVariant(
                source.getProductFamily(), request.newVariant().name(), request.newVariant().unitQuantity(),
                request.newVariant().unit(), request.newVariant().packageQuantity(), request.newVariant().packageDescription(),
                request.newVariant().totalQuantity(), request.newVariant().totalUnit(), request.newVariant().gtin()),
                selectedVariantItems(source, request.receiptItemIds()));
    }

    @Transactional
    public ProductChangePreviewDto applyVariantSplit(ProductVariantSplitApplyRequest request) {
        ProductVariant source = variant(request.sourceVariantId());
        requireMatchingFamily(source, request.newVariant());
        if (Boolean.FALSE.equals(request.newVariant().isActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Neue Produktvariante muss aktiv sein.");
        }
        List<ReceiptItem> items = selectedVariantItems(source, request.receiptItemIds());
        ProductVariant target = productVariantRepository.saveAndFlush(new ProductVariant(
                source.getProductFamily(), request.newVariant().name(), request.newVariant().unitQuantity(),
                request.newVariant().unit(), request.newVariant().packageQuantity(), request.newVariant().packageDescription(),
                request.newVariant().totalQuantity(), request.newVariant().totalUnit(), request.newVariant().gtin()));
        ProductChangePreviewDto preview = preview(source, target, items);
        for (ReceiptItem item : items) {
            assignAndAudit(item, source.getProductFamily(), target, "VARIANT_SPLIT");
        }
        return preview;
    }

    private List<ReceiptItem> sourceItems(ProductFamily source) {
        return receiptItemRepository.findByProductFamily_Id(source.getId()).stream()
                .filter(item -> item.getReceipt() != null && item.getReceipt().getDeletedAt() == null)
                .toList();
    }

    private List<ReceiptItem> sourceItems(ProductVariant source) {
        return receiptItemRepository.findByProductVariant_Id(source.getId()).stream()
                .filter(item -> item.getReceipt() != null && item.getReceipt().getDeletedAt() == null)
                .toList();
    }

    private List<ReceiptItem> selectedFamilyItems(ProductFamily source, java.util.Set<Long> ids) {
        List<ReceiptItem> items = sourceItems(source).stream()
                .filter(item -> item.getProductVariant() == null && ids.contains(item.getId()))
                .toList();
        if (items.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Jede ausgewaehlte Position muss aktiv sein und genau der Quellfamilie ohne Variante angehoeren.");
        }
        return items;
    }

    private List<ReceiptItem> selectedVariantItems(ProductVariant source, java.util.Set<Long> ids) {
        List<ReceiptItem> items = sourceItems(source).stream()
                .filter(item -> ids.contains(item.getId()))
                .toList();
        if (items.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Jede ausgewaehlte Position muss aktiv sein und der Quellvariante angehoeren.");
        }
        return items;
    }

    private ProductChangePreviewDto preview(ProductFamily source, ProductFamily target, List<ReceiptItem> items) {
        List<String> stores = items.stream()
                .map(item -> item.getReceipt().getStoreName())
                .filter(store -> store != null && !store.isBlank())
                .distinct()
                .sorted()
                .toList();
        LocalDate dateFrom = items.stream()
                .map(item -> item.getReceipt().getReceiptDate())
                .filter(date -> date != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate dateTo = items.stream()
                .map(item -> item.getReceipt().getReceiptDate())
                .filter(date -> date != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ProductChangePreviewDto(
                items.size(), stores, dateFrom, dateTo,
                source.getId(), source.getName(), target.getId(), target.getName(),
                null, null, null, null,
                "Preisreports werden erst in Phase 15c neu berechnet.");
    }

    private ProductChangePreviewDto preview(ProductVariant source, ProductVariant target, List<ReceiptItem> items) {
        List<String> stores = affectedStores(items);
        return new ProductChangePreviewDto(
                items.size(), stores, dateFrom(items), dateTo(items),
                source.getProductFamily().getId(), source.getProductFamily().getName(),
                target.getProductFamily().getId(), target.getProductFamily().getName(),
                source.getId(), source.getName(), target.getId(), target.getName(),
                "Preisreports werden erst in Phase 15c neu berechnet.");
    }

    private List<String> affectedStores(List<ReceiptItem> items) {
        return items.stream()
                .map(item -> item.getReceipt().getStoreName())
                .filter(store -> store != null && !store.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private LocalDate dateFrom(List<ReceiptItem> items) {
        return items.stream().map(item -> item.getReceipt().getReceiptDate()).filter(date -> date != null)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    private LocalDate dateTo(List<ReceiptItem> items) {
        return items.stream().map(item -> item.getReceipt().getReceiptDate()).filter(date -> date != null)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    private void assignAndAudit(ReceiptItem item, ProductFamily family, ProductVariant variant, String reason) {
        item.assignProduct(family, variant, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED, null);
        productAssignmentLogRepository.save(new ProductAssignmentLog(
                item, family, variant, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED,
                null, null, reason));
    }

    private ProductFamily family(Long id) {
        return productFamilyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produktfamilie nicht gefunden."));
    }

    private ProductVariant variant(Long id) {
        return productVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produktvariante nicht gefunden."));
    }

    private void requireDistinct(ProductFamily source, ProductFamily target) {
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Quell- und Zielfamilie muessen unterschiedlich sein.");
        }
    }

    private void requireDistinct(ProductVariant source, ProductVariant target) {
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Quell- und Zielvariante muessen unterschiedlich sein.");
        }
    }

    private void requireSameFamily(ProductVariant source, ProductVariant target) {
        if (!source.getProductFamily().getId().equals(target.getProductFamily().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Varianten duerfen nur innerhalb derselben Produktfamilie zusammengefuehrt werden.");
        }
    }

    private void requireMatchingFamily(ProductVariant source, ProductVariantRequest request) {
        if (!source.getProductFamily().getId().equals(request.productFamilyId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Neue Produktvariante muss zur Produktfamilie der Quellvariante gehoeren.");
        }
    }

    private void ensureNewFamilyNameAvailable(String name) {
        productFamilyRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produktfamilie existiert bereits.");
        });
    }

    private Category activeCategoryOrNull(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Kategorie nicht gefunden."));
        if (!category.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Standard-Kategorie ist deaktiviert.");
        }
        return category;
    }

    private void requireFamilyWithoutVariants(ProductFamily family) {
        if (!productVariantRepository.findByProductFamily_IdOrderByNameAsc(family.getId()).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Produktfamilie hat Varianten. Bitte Varianten zuerst getrennt behandeln.");
        }
    }
}
