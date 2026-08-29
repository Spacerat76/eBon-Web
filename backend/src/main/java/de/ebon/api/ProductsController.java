package de.ebon.api;

import de.ebon.api.dto.AuditProductCorrectionRequest;
import de.ebon.api.dto.AuditProductCorrectionResponse;
import de.ebon.api.dto.ProductAssignmentRunRequest;
import de.ebon.api.dto.ProductAssignmentRunResponse;
import de.ebon.api.dto.ProductAssignmentCorrectionRequest;
import de.ebon.api.dto.ProductFamilyDto;
import de.ebon.api.dto.ProductFamilyMergeApplyRequest;
import de.ebon.api.dto.ProductFamilyMergeRequest;
import de.ebon.api.dto.ProductFamilySplitApplyRequest;
import de.ebon.api.dto.ProductFamilySplitRequest;
import de.ebon.api.dto.ProductFamilyRequest;
import de.ebon.api.dto.ProductRuleDto;
import de.ebon.api.dto.ProductRulePreviewRequest;
import de.ebon.api.dto.ProductRulePreviewResponse;
import de.ebon.api.dto.ProductRuleApplyRequest;
import de.ebon.api.dto.ProductRuleRequest;
import de.ebon.api.dto.ProductRuleSuggestionAcceptRequest;
import de.ebon.api.dto.ProductRuleSuggestionAcceptResponse;
import de.ebon.api.dto.ProductRuleSuggestionDto;
import de.ebon.api.dto.ProductRuleSuggestionRequest;
import de.ebon.api.dto.ProductVariantDto;
import de.ebon.api.dto.ProductVariantRequest;
import de.ebon.api.dto.ProductVariantMergeApplyRequest;
import de.ebon.api.dto.ProductVariantMergeRequest;
import de.ebon.api.dto.ProductVariantSplitApplyRequest;
import de.ebon.api.dto.ProductVariantSplitRequest;
import de.ebon.product.ProductManagementService;
import de.ebon.product.ProductMaintenanceService;
import de.ebon.product.ProductReviewService;
import de.ebon.product.AuditProductCorrectionService;
import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ProductReviewItemDto;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Produkte")
@SecurityRequirement(name = "bearerAuth")
public class ProductsController {

    private final ProductManagementService productManagementService;
    private final ProductReviewService productReviewService;
    private final ProductMaintenanceService productMaintenanceService;
    private final AuditProductCorrectionService auditProductCorrectionService;

    public ProductsController(
            ProductManagementService productManagementService,
            ProductReviewService productReviewService,
            ProductMaintenanceService productMaintenanceService,
            AuditProductCorrectionService auditProductCorrectionService) {
        this.productManagementService = productManagementService;
        this.productReviewService = productReviewService;
        this.productMaintenanceService = productMaintenanceService;
        this.auditProductCorrectionService = auditProductCorrectionService;
    }

    @GetMapping("/api/products/families")
    public List<ProductFamilyDto> families() {
        return productManagementService.families();
    }

    @PostMapping("/api/products/families")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductFamilyDto createFamily(@Valid @RequestBody ProductFamilyRequest request) {
        return productManagementService.createFamily(request);
    }

    @PutMapping("/api/products/families/{id}")
    public ProductFamilyDto updateFamily(@PathVariable Long id, @Valid @RequestBody ProductFamilyRequest request) {
        return productManagementService.updateFamily(id, request);
    }

    @GetMapping("/api/products/variants")
    public List<ProductVariantDto> variants(@RequestParam(required = false) Long productFamilyId) {
        return productManagementService.variants(productFamilyId);
    }

    @PostMapping("/api/products/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductVariantDto createVariant(@Valid @RequestBody ProductVariantRequest request) {
        return productManagementService.createVariant(request);
    }

    @PutMapping("/api/products/variants/{id}")
    public ProductVariantDto updateVariant(@PathVariable Long id, @Valid @RequestBody ProductVariantRequest request) {
        return productManagementService.updateVariant(id, request);
    }

    @GetMapping("/api/products/rules")
    public List<ProductRuleDto> rules() {
        return productManagementService.rules();
    }

    @PostMapping("/api/products/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductRuleDto createRule(@Valid @RequestBody ProductRuleRequest request) {
        return productManagementService.createRule(request);
    }

    @PutMapping("/api/products/rules/{id}")
    public ProductRuleDto updateRule(@PathVariable Long id, @Valid @RequestBody ProductRuleRequest request) {
        return productManagementService.updateRule(id, request);
    }

    @DeleteMapping("/api/products/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) {
        productManagementService.deleteRule(id);
    }

    @PostMapping("/api/products/rules/preview")
    @Operation(summary = "Produktregel auf bestehende Positionen vorschauen")
    public ProductRulePreviewResponse previewRule(@Valid @RequestBody ProductRulePreviewRequest request) {
        return new ProductRulePreviewResponse(productManagementService.preview(request));
    }

    @PostMapping("/api/products/rules/{id}/apply")
    public ProductAssignmentRunResponse applyRule(
            @PathVariable Long id,
            @Valid @RequestBody ProductRuleApplyRequest request) {
        return new ProductAssignmentRunResponse(productManagementService.applyRule(id));
    }

    @PostMapping("/api/products/assignments/run")
    public ProductAssignmentRunResponse runAssignments(@RequestBody(required = false) ProductAssignmentRunRequest request) {
        return new ProductAssignmentRunResponse(productManagementService.runAssignments(
                request == null ? new ProductAssignmentRunRequest(null, true) : request));
    }

    @GetMapping("/api/products/review")
    @Operation(summary = "Produktzuordnungen zur manuellen Pruefung auflisten")
    public PageResponse<ProductReviewItemDto> reviewQueue(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) ProductAssignmentSource source,
            @RequestParam(required = false) ProductAssignmentStatus status,
            @RequestParam(required = false) @DecimalMin("0.000") @DecimalMax("1.000") BigDecimal confidenceMax) {
        return productReviewService.queue(
                page, size, store, productFamilyId, categoryId, dateFrom, dateTo, source, status, confidenceMax);
    }

    @PostMapping("/api/products/review/{receiptItemId}/accept")
    @Operation(summary = "Produktvorschlag als manuelle Zuordnung bestaetigen")
    public ProductReviewItemDto acceptReview(@PathVariable Long receiptItemId) {
        return productReviewService.accept(receiptItemId);
    }

    @PostMapping("/api/products/review/{receiptItemId}/correct")
    @Operation(summary = "Produktzuordnung manuell korrigieren")
    public ProductReviewItemDto correctReview(
            @PathVariable Long receiptItemId,
            @Valid @RequestBody ProductAssignmentCorrectionRequest request) {
        return productReviewService.correct(receiptItemId, request);
    }

    @PostMapping("/api/products/review/{receiptItemId}/audit-correct")
    @Operation(summary = "Eindeutige Codex-Audit-Korrektur mit KI-Provenienz anwenden")
    public AuditProductCorrectionResponse auditCorrectReview(
            @PathVariable Long receiptItemId,
            @Valid @RequestBody AuditProductCorrectionRequest request) {
        return auditProductCorrectionService.correct(receiptItemId, request);
    }

    @PostMapping("/api/products/review/{receiptItemId}/reject")
    @Operation(summary = "Produktvorschlag ablehnen")
    public ProductReviewItemDto rejectReview(@PathVariable Long receiptItemId) {
        return productReviewService.reject(receiptItemId);
    }

    @PostMapping("/api/products/review/{receiptItemId}/no-product")
    @Operation(summary = "Position als keine Produktposition markieren")
    public ProductReviewItemDto markReviewNoProduct(@PathVariable Long receiptItemId) {
        return productReviewService.markNoProduct(receiptItemId);
    }

    @DeleteMapping("/api/products/review/{receiptItemId}/assignment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Produktzuordnung von einer Bon-Position entfernen")
    public void clearReviewAssignment(@PathVariable Long receiptItemId) {
        productReviewService.clearAssignment(receiptItemId);
    }

    @PostMapping("/api/products/review/{receiptItemId}/rule-suggestion")
    @Operation(summary = "Produktregel aus einer manuellen Zuordnung mit Vorschau vorschlagen")
    public ProductRuleSuggestionDto suggestRule(
            @PathVariable Long receiptItemId,
            @Valid @RequestBody ProductRuleSuggestionRequest request) {
        return productReviewService.suggestRule(receiptItemId, request);
    }

    @PostMapping("/api/products/review/{receiptItemId}/rule-suggestion/accept")
    @Operation(summary = "Vorgeschlagene Produktregel bestaetigen und optional rueckwirkend anwenden")
    public ProductRuleSuggestionAcceptResponse acceptRuleSuggestion(
            @PathVariable Long receiptItemId,
            @Valid @RequestBody ProductRuleSuggestionAcceptRequest request) {
        return productReviewService.acceptRuleSuggestion(receiptItemId, request);
    }

    @PostMapping("/api/products/families/merge/preview")
    @Operation(summary = "Zusammenfuehren von Produktfamilien vorschauen")
    public de.ebon.api.dto.ProductChangePreviewDto previewFamilyMerge(
            @Valid @RequestBody ProductFamilyMergeRequest request) {
        return productMaintenanceService.previewFamilyMerge(request);
    }

    @PostMapping("/api/products/families/merge/apply")
    @Operation(summary = "Zusammenfuehren von Produktfamilien nach Bestaetigung anwenden")
    public de.ebon.api.dto.ProductChangePreviewDto applyFamilyMerge(
            @Valid @RequestBody ProductFamilyMergeApplyRequest request) {
        return productMaintenanceService.applyFamilyMerge(request);
    }

    @PostMapping("/api/products/families/split/preview")
    @Operation(summary = "Trennen einer Produktfamilie vorschauen")
    public de.ebon.api.dto.ProductChangePreviewDto previewFamilySplit(
            @Valid @RequestBody ProductFamilySplitRequest request) {
        return productMaintenanceService.previewFamilySplit(request);
    }

    @PostMapping("/api/products/families/split/apply")
    @Operation(summary = "Trennen einer Produktfamilie nach Bestaetigung anwenden")
    public de.ebon.api.dto.ProductChangePreviewDto applyFamilySplit(
            @Valid @RequestBody ProductFamilySplitApplyRequest request) {
        return productMaintenanceService.applyFamilySplit(request);
    }

    @PostMapping("/api/products/variants/merge/preview")
    @Operation(summary = "Zusammenfuehren von Produktvarianten vorschauen")
    public de.ebon.api.dto.ProductChangePreviewDto previewVariantMerge(
            @Valid @RequestBody ProductVariantMergeRequest request) {
        return productMaintenanceService.previewVariantMerge(request);
    }

    @PostMapping("/api/products/variants/merge/apply")
    @Operation(summary = "Zusammenfuehren von Produktvarianten nach Bestaetigung anwenden")
    public de.ebon.api.dto.ProductChangePreviewDto applyVariantMerge(
            @Valid @RequestBody ProductVariantMergeApplyRequest request) {
        return productMaintenanceService.applyVariantMerge(request);
    }

    @PostMapping("/api/products/variants/split/preview")
    @Operation(summary = "Trennen einer Produktvariante vorschauen")
    public de.ebon.api.dto.ProductChangePreviewDto previewVariantSplit(
            @Valid @RequestBody ProductVariantSplitRequest request) {
        return productMaintenanceService.previewVariantSplit(request);
    }

    @PostMapping("/api/products/variants/split/apply")
    @Operation(summary = "Trennen einer Produktvariante nach Bestaetigung anwenden")
    public de.ebon.api.dto.ProductChangePreviewDto applyVariantSplit(
            @Valid @RequestBody ProductVariantSplitApplyRequest request) {
        return productMaintenanceService.applyVariantSplit(request);
    }
}
