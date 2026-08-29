package de.ebon.product;

import de.ebon.api.dto.AuditExpectedProductAssignment;
import de.ebon.api.dto.AuditProductCorrectionRequest;
import de.ebon.api.dto.AuditProductCorrectionResponse;
import de.ebon.api.dto.AuditProductVariantRequest;
import de.ebon.persistence.model.ProductAssignmentLog;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditProductCorrectionService {

    private static final String MODEL = "codex-interactive-audit";

    private final ReceiptItemRepository receiptItemRepository;
    private final ProductFamilyRepository productFamilyRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductAssignmentLogRepository assignmentLogRepository;

    public AuditProductCorrectionService(
            ReceiptItemRepository receiptItemRepository,
            ProductFamilyRepository productFamilyRepository,
            ProductVariantRepository productVariantRepository,
            ProductAssignmentLogRepository assignmentLogRepository) {
        this.receiptItemRepository = receiptItemRepository;
        this.productFamilyRepository = productFamilyRepository;
        this.productVariantRepository = productVariantRepository;
        this.assignmentLogRepository = assignmentLogRepository;
    }

    @Transactional
    public AuditProductCorrectionResponse correct(Long receiptItemId, AuditProductCorrectionRequest request) {
        ReceiptItem item = receiptItemRepository.findByIdForProductAudit(receiptItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bon-Position nicht gefunden."));
        requireExpectedState(item, request.expected());
        requireUnprotected(item);

        boolean familyCreated = request.productFamilyId() == null;
        ProductFamily family = familyCreated ? createFamily(request.newProductFamilyName(), item) : existingFamily(request.productFamilyId());
        boolean variantCreated = request.newProductVariant() != null;
        ProductVariant variant = variantCreated
                ? createVariant(family, request.newProductVariant())
                : existingVariant(request.productVariantId(), family);

        item.assignProduct(
                family, variant, ProductAssignmentSource.AI,
                ProductAssignmentStatus.AUTO_ASSIGNED, request.confidence());
        assignmentLogRepository.save(new ProductAssignmentLog(
                item, family, variant, ProductAssignmentSource.AI,
                ProductAssignmentStatus.AUTO_ASSIGNED, request.confidence(), MODEL, request.reasonCode()));
        return new AuditProductCorrectionResponse(
                item.getId(), family.getId(), variant == null ? null : variant.getId(),
                ProductAssignmentSource.AI, ProductAssignmentStatus.AUTO_ASSIGNED,
                request.confidence(), familyCreated, variantCreated);
    }

    private void requireExpectedState(ReceiptItem item, AuditExpectedProductAssignment expected) {
        if (!Objects.equals(id(item.getProductFamily()), expected.productFamilyId())
                || !Objects.equals(id(item.getProductVariant()), expected.productVariantId())
                || item.getProductAssignmentSource() != expected.source()
                || item.getProductAssignmentStatus() != expected.status()) {
            throw conflict("Produktzuordnung wurde seit der Audit-Vorschau verändert.");
        }
    }

    private void requireUnprotected(ReceiptItem item) {
        ProductAssignmentStatus status = item.getProductAssignmentStatus();
        if (item.getProductAssignmentSource() == ProductAssignmentSource.MANUAL
                || status == ProductAssignmentStatus.CONFIRMED
                || status == ProductAssignmentStatus.NO_PRODUCT
                || status == ProductAssignmentStatus.REJECTED) {
            throw conflict("Produktzuordnung ist manuell oder bestätigt geschützt.");
        }
    }

    private ProductFamily existingFamily(Long familyId) {
        return productFamilyRepository.findById(familyId)
                .filter(ProductFamily::isActive)
                .orElseThrow(() -> conflict("Aktive Produktfamilie nicht gefunden."));
    }

    private ProductFamily createFamily(String name, ReceiptItem item) {
        String trimmed = name == null ? "" : name.trim();
        productFamilyRepository.findByNameIgnoreCase(trimmed).ifPresent(existing -> {
            throw conflict("Produktfamilie existiert bereits.");
        });
        return productFamilyRepository.saveAndFlush(new ProductFamily(trimmed, item.getCategory()));
    }

    private ProductVariant existingVariant(Long variantId, ProductFamily family) {
        if (variantId == null) {
            return null;
        }
        ProductVariant variant = productVariantRepository.findById(variantId)
                .filter(ProductVariant::isActive)
                .orElseThrow(() -> conflict("Aktive Produktvariante nicht gefunden."));
        if (!Objects.equals(variant.getProductFamily().getId(), family.getId())) {
            throw conflict("Produktvariante gehört nicht zur Produktfamilie.");
        }
        return variant;
    }

    private ProductVariant createVariant(ProductFamily family, AuditProductVariantRequest request) {
        return productVariantRepository.saveAndFlush(new ProductVariant(
                family, request.name(), request.unitQuantity(), request.unit(), request.packageQuantity(),
                request.packageDescription(), request.totalQuantity(), request.totalUnit(), request.gtin()));
    }

    private Long id(ProductFamily family) {
        return family == null ? null : family.getId();
    }

    private Long id(ProductVariant variant) {
        return variant == null ? null : variant.getId();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
