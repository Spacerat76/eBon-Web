package de.ebon.product;

import de.ebon.api.dto.ProductAssignmentRunRequest;
import de.ebon.api.dto.ProductFamilyDto;
import de.ebon.api.dto.ProductFamilyRequest;
import de.ebon.api.dto.ProductRuleDto;
import de.ebon.api.dto.ProductRulePreviewRequest;
import de.ebon.api.dto.ProductRuleRequest;
import de.ebon.api.dto.ProductVariantDto;
import de.ebon.api.dto.ProductVariantRequest;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductRule;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductRuleRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductManagementService {

    private final ProductFamilyRepository productFamilyRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRuleRepository productRuleRepository;
    private final CategoryRepository categoryRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final ProductRuleMatcher productRuleMatcher;
    private final ProductAssignmentService productAssignmentService;

    public ProductManagementService(
            ProductFamilyRepository productFamilyRepository,
            ProductVariantRepository productVariantRepository,
            ProductRuleRepository productRuleRepository,
            CategoryRepository categoryRepository,
            ReceiptItemRepository receiptItemRepository,
            ProductRuleMatcher productRuleMatcher,
            ProductAssignmentService productAssignmentService) {
        this.productFamilyRepository = productFamilyRepository;
        this.productVariantRepository = productVariantRepository;
        this.productRuleRepository = productRuleRepository;
        this.categoryRepository = categoryRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.productRuleMatcher = productRuleMatcher;
        this.productAssignmentService = productAssignmentService;
    }

    @Transactional(readOnly = true)
    public List<ProductFamilyDto> families() {
        return productFamilyRepository.findAll().stream().map(this::toFamilyDto).toList();
    }

    @Transactional
    public ProductFamilyDto createFamily(ProductFamilyRequest request) {
        productFamilyRepository.findByNameIgnoreCase(request.name().trim()).ifPresent(existing -> {
            throw new IllegalArgumentException("Produktfamilie existiert bereits.");
        });
        ProductFamily family = new ProductFamily(request.name(), activeCategoryOrNull(request.defaultCategoryId()));
        if (Boolean.FALSE.equals(request.isActive())) {
            family.update(family.getName(), family.getDefaultCategory(), false);
        }
        return toFamilyDto(productFamilyRepository.saveAndFlush(family));
    }

    @Transactional
    public ProductFamilyDto updateFamily(Long id, ProductFamilyRequest request) {
        ProductFamily family = family(id);
        family.update(request.name(), activeCategoryOrNull(request.defaultCategoryId()), !Boolean.FALSE.equals(request.isActive()));
        return toFamilyDto(family);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantDto> variants(Long productFamilyId) {
        List<ProductVariant> variants = productFamilyId == null
                ? productVariantRepository.findAll()
                : productVariantRepository.findByProductFamily_IdOrderByNameAsc(productFamilyId);
        return variants.stream().map(this::toVariantDto).toList();
    }

    @Transactional
    public ProductVariantDto createVariant(ProductVariantRequest request) {
        ProductVariant variant = new ProductVariant(
                family(request.productFamilyId()),
                request.name(),
                request.unitQuantity(),
                request.unit(),
                request.packageQuantity(),
                request.packageDescription(),
                request.totalQuantity(),
                request.totalUnit(),
                request.gtin());
        if (Boolean.FALSE.equals(request.isActive())) {
            variant.updateValues(
                    request.name(), request.unitQuantity(), request.unit(), request.packageQuantity(),
                    request.packageDescription(), request.totalQuantity(), request.totalUnit(), request.gtin(), false);
        }
        return toVariantDto(productVariantRepository.saveAndFlush(variant));
    }

    @Transactional
    public ProductVariantDto updateVariant(Long id, ProductVariantRequest request) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produktvariante nicht gefunden."));
        if (!variant.getProductFamily().getId().equals(request.productFamilyId())) {
            throw new IllegalArgumentException("Produktvariante kann in Phase 15a nicht in eine andere Familie verschoben werden.");
        }
        variant.updateValues(
                request.name(), request.unitQuantity(), request.unit(), request.packageQuantity(),
                request.packageDescription(), request.totalQuantity(), request.totalUnit(), request.gtin(),
                !Boolean.FALSE.equals(request.isActive()));
        return toVariantDto(variant);
    }

    @Transactional(readOnly = true)
    public List<ProductRuleDto> rules() {
        return productRuleRepository.findAll().stream().map(this::toRuleDto).toList();
    }

    @Transactional
    public ProductRuleDto createRule(ProductRuleRequest request) {
        ProductRule rule = new ProductRule(
                family(request.productFamilyId()),
                variantForFamily(request.productVariantId(), request.productFamilyId()),
                request.storeName(),
                request.matchType(),
                request.matchValue(),
                request.priority() == null ? 100 : request.priority());
        if (Boolean.FALSE.equals(request.isActive())) {
            rule.update(
                    rule.getProductFamily(), rule.getProductVariant(), rule.getStoreName(), rule.getMatchType(),
                    rule.getMatchValue(), rule.getPriority(), false);
        }
        return toRuleDto(productRuleRepository.saveAndFlush(rule));
    }

    @Transactional
    public ProductRuleDto updateRule(Long id, ProductRuleRequest request) {
        ProductRule rule = rule(id);
        rule.update(
                family(request.productFamilyId()),
                variantForFamily(request.productVariantId(), request.productFamilyId()),
                request.storeName(),
                request.matchType(),
                request.matchValue(),
                request.priority() == null ? 100 : request.priority(),
                !Boolean.FALSE.equals(request.isActive()));
        return toRuleDto(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        productRuleRepository.delete(rule(id));
    }

    @Transactional(readOnly = true)
    public long preview(ProductRulePreviewRequest request) {
        ProductFamily placeholderFamily = new ProductFamily("Vorschau", null);
        ProductRule previewRule = new ProductRule(
                placeholderFamily,
                null,
                request.storeName(),
                request.matchType(),
                request.matchValue(),
                100);
        return receiptItemRepository.findAll().stream()
                .filter(item -> item.getReceipt() != null && item.getReceipt().getDeletedAt() == null)
                .filter(item -> productRuleMatcher.matches(previewRule, item))
                .count();
    }

    @Transactional
    public int applyRule(Long id) {
        return productAssignmentService.applyRuleToExistingItems(rule(id));
    }

    @Transactional
    public int runAssignments(ProductAssignmentRunRequest request) {
        if (request.receiptId() != null) {
            return productAssignmentService.assignReceipt(request.receiptId());
        }
        return productAssignmentService.assignOpenItems();
    }

    private ProductFamily family(Long id) {
        return productFamilyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produktfamilie nicht gefunden."));
    }

    private Category activeCategoryOrNull(Long id) {
        if (id == null) {
            return null;
        }
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Kategorie nicht gefunden."));
        if (!category.isActive()) {
            throw new IllegalArgumentException("Standard-Kategorie ist deaktiviert.");
        }
        return category;
    }

    private ProductVariant variantForFamily(Long variantId, Long familyId) {
        if (variantId == null) {
            return null;
        }
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Produktvariante nicht gefunden."));
        if (!variant.getProductFamily().getId().equals(familyId)) {
            throw new IllegalArgumentException("Produktvariante gehoert nicht zur Produktfamilie.");
        }
        return variant;
    }

    private ProductRule rule(Long id) {
        return productRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produktregel nicht gefunden."));
    }

    private ProductFamilyDto toFamilyDto(ProductFamily family) {
        Category category = family.getDefaultCategory();
        return new ProductFamilyDto(
                family.getId(), family.getName(), category == null ? null : category.getId(),
                category == null ? null : category.getName(), family.isActive(), family.getCreatedAt(), family.getUpdatedAt());
    }

    private ProductVariantDto toVariantDto(ProductVariant variant) {
        return new ProductVariantDto(
                variant.getId(), variant.getProductFamily().getId(), variant.getProductFamily().getName(), variant.getName(),
                variant.getUnitQuantity(), variant.getUnit(), variant.getPackageQuantity(), variant.getPackageDescription(),
                variant.getTotalQuantity(), variant.getTotalUnit(), variant.getGtin(), variant.isActive());
    }

    private ProductRuleDto toRuleDto(ProductRule rule) {
        ProductVariant variant = rule.getProductVariant();
        return new ProductRuleDto(
                rule.getId(), rule.getProductFamily().getId(), rule.getProductFamily().getName(),
                variant == null ? null : variant.getId(), variant == null ? null : variant.getName(), rule.getStoreName(),
                rule.getMatchType(), rule.getMatchValue(), rule.getPriority(), rule.isActive());
    }
}
