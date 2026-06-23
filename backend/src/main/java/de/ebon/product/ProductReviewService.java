package de.ebon.product;

import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ProductAssignmentCorrectionRequest;
import de.ebon.api.dto.ProductReviewItemDto;
import de.ebon.api.dto.ProductRulePreviewRequest;
import de.ebon.api.dto.ProductRulePreviewResponse;
import de.ebon.api.dto.ProductRuleRequest;
import de.ebon.api.dto.ProductRuleSuggestionAcceptRequest;
import de.ebon.api.dto.ProductRuleSuggestionAcceptResponse;
import de.ebon.api.dto.ProductRuleSuggestionDto;
import de.ebon.api.dto.ProductRuleSuggestionRequest;
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
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ProductReviewService {

    private static final BigDecimal DEFAULT_REVIEW_CONFIDENCE = new BigDecimal("0.900");
    private static final int MAX_PAGE_SIZE = 100;

    private final ReceiptItemRepository receiptItemRepository;
    private final ProductAssignmentLogRepository assignmentLogRepository;
    private final ProductFamilyRepository productFamilyRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductManagementService productManagementService;

    public ProductReviewService(
            ReceiptItemRepository receiptItemRepository,
            ProductAssignmentLogRepository assignmentLogRepository,
            ProductFamilyRepository productFamilyRepository,
            ProductVariantRepository productVariantRepository,
            ProductManagementService productManagementService) {
        this.receiptItemRepository = receiptItemRepository;
        this.assignmentLogRepository = assignmentLogRepository;
        this.productFamilyRepository = productFamilyRepository;
        this.productVariantRepository = productVariantRepository;
        this.productManagementService = productManagementService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductReviewItemDto> queue(
            int page,
            int size,
            String store,
            Long productFamilyId,
            Long categoryId,
            LocalDate dateFrom,
            LocalDate dateTo,
            ProductAssignmentSource source,
            ProductAssignmentStatus status,
            BigDecimal confidenceMax) {
        List<ReceiptItem> activeItems = receiptItemRepository.findAll().stream()
                .filter(item -> item.getReceipt() != null && item.getReceipt().getDeletedAt() == null)
                .toList();
        Map<String, Long> occurrences = activeItems.stream()
                .collect(Collectors.groupingBy(this::contextKey, Collectors.counting()));
        BigDecimal threshold = validConfidence(confidenceMax) ? confidenceMax : DEFAULT_REVIEW_CONFIDENCE;

        List<ProductReviewItemDto> content = activeItems.stream()
                .map(item -> toDto(item, occurrences.getOrDefault(contextKey(item), 1L)))
                .filter(item -> isReviewCandidate(item, threshold))
                .filter(item -> store == null || store.isBlank()
                        || containsIgnoreCase(item.storeName(), store))
                .filter(item -> productFamilyId == null || productFamilyId.equals(item.currentProductFamilyId())
                        || productFamilyId.equals(item.suggestedProductFamilyId()))
                .filter(item -> categoryId == null || categoryId.equals(item.categoryId()))
                .filter(item -> dateFrom == null || item.receiptDate() == null || !item.receiptDate().isBefore(dateFrom))
                .filter(item -> dateTo == null || item.receiptDate() == null || !item.receiptDate().isAfter(dateTo))
                .filter(item -> source == null || source == item.assignmentSource())
                .filter(item -> status == null || status == item.assignmentStatus())
                .sorted(Comparator
                        .comparingLong(ProductReviewItemDto::possibleRetroactiveItems).reversed()
                        .thenComparing(ProductReviewItemDto::totalPrice,
                                Comparator.nullsLast(Comparator.<BigDecimal, BigDecimal>comparing(BigDecimal::abs).reversed()))
                        .thenComparing(ProductReviewItemDto::receiptDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductReviewItemDto::receiptItemId, Comparator.reverseOrder()))
                .toList();

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * safeSize, content.size());
        int toIndex = Math.min(fromIndex + safeSize, content.size());
        return new PageResponse<>(
                content.subList(fromIndex, toIndex),
                safePage,
                safeSize,
                content.size(),
                (int) Math.ceil((double) content.size() / safeSize),
                "reviewPriority",
                "desc");
    }

    @Transactional
    public ProductReviewItemDto accept(Long receiptItemId) {
        ReceiptItem item = item(receiptItemId);
        Proposal proposal = proposal(item);
        if (proposal.family() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kein Produktvorschlag zum Uebernehmen vorhanden.");
        }
        assignManual(item, proposal.family(), proposal.variant(), "MANUAL_ACCEPT");
        return toDto(item, 1L);
    }

    @Transactional
    public ProductReviewItemDto correct(Long receiptItemId, ProductAssignmentCorrectionRequest request) {
        ReceiptItem item = item(receiptItemId);
        ProductFamily family = productFamilyRepository.findById(request.productFamilyId())
                .orElseThrow(() -> new EntityNotFoundException("Produktfamilie nicht gefunden."));
        ProductVariant variant = request.productVariantId() == null ? null : productVariantRepository.findById(request.productVariantId())
                .orElseThrow(() -> new EntityNotFoundException("Produktvariante nicht gefunden."));
        if (variant != null && !variant.getProductFamily().getId().equals(family.getId())) {
            throw new IllegalArgumentException("Produktvariante gehoert nicht zur Produktfamilie.");
        }
        assignManual(item, family, variant, "MANUAL_CORRECTION");
        return toDto(item, 1L);
    }

    @Transactional
    public ProductReviewItemDto reject(Long receiptItemId) {
        ReceiptItem item = item(receiptItemId);
        item.markProductRejected();
        audit(item, null, null, ProductAssignmentStatus.REJECTED, "MANUAL_REJECT");
        return toDto(item, 1L);
    }

    @Transactional
    public ProductReviewItemDto markNoProduct(Long receiptItemId) {
        ReceiptItem item = item(receiptItemId);
        item.markNoProduct();
        audit(item, null, null, ProductAssignmentStatus.NO_PRODUCT, "MANUAL_NO_PRODUCT");
        return toDto(item, 1L);
    }

    @Transactional
    public void clearAssignment(Long receiptItemId) {
        ReceiptItem item = item(receiptItemId);
        item.clearProductAssignment();
        audit(item, null, null, ProductAssignmentStatus.REJECTED, "MANUAL_CLEAR_ASSIGNMENT");
    }

    @Transactional(readOnly = true)
    public ProductRuleSuggestionDto suggestRule(Long receiptItemId, ProductRuleSuggestionRequest request) {
        ReceiptItem item = item(receiptItemId);
        if (item.getProductFamily() == null || item.getProductAssignmentSource() != ProductAssignmentSource.MANUAL) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Regelvorschlaege benoetigen zuerst eine manuell bestaetigte Produktzuordnung.");
        }
        ProductRuleRequest rule = new ProductRuleRequest(
                item.getProductFamily().getId(),
                item.getProductVariant() == null ? null : item.getProductVariant().getId(),
                Boolean.TRUE.equals(request.storeSpecific()) ? item.getReceipt().getStoreName() : null,
                request.matchType(),
                item.getDescription(),
                request.priority() == null ? 100 : request.priority(),
                true);
        long matchingItems = productManagementService.preview(new ProductRulePreviewRequest(
                rule.storeName(), rule.matchType(), rule.matchValue()));
        return new ProductRuleSuggestionDto(rule, new ProductRulePreviewResponse(matchingItems));
    }

    @Transactional
    public ProductRuleSuggestionAcceptResponse acceptRuleSuggestion(
            Long receiptItemId,
            ProductRuleSuggestionAcceptRequest request) {
        ReceiptItem item = item(receiptItemId);
        if (item.getProductFamily() == null || item.getProductAssignmentSource() != ProductAssignmentSource.MANUAL) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Regelvorschlaege benoetigen zuerst eine manuell bestaetigte Produktzuordnung.");
        }
        var rule = productManagementService.createRule(request.rule());
        long changedItems = Boolean.TRUE.equals(request.applyToExisting())
                ? productManagementService.applyRule(rule.id())
                : 0;
        return new ProductRuleSuggestionAcceptResponse(rule, changedItems);
    }

    private void assignManual(ReceiptItem item, ProductFamily family, ProductVariant variant, String reason) {
        item.assignProduct(family, variant, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED, null);
        audit(item, family, variant, ProductAssignmentStatus.CONFIRMED, reason);
    }

    private void audit(
            ReceiptItem item,
            ProductFamily family,
            ProductVariant variant,
            ProductAssignmentStatus status,
            String reason) {
        assignmentLogRepository.save(new ProductAssignmentLog(
                item,
                family,
                variant,
                ProductAssignmentSource.MANUAL,
                status,
                null,
                "manual-review",
                reason));
    }

    private ReceiptItem item(Long receiptItemId) {
        return receiptItemRepository.findById(receiptItemId)
                .filter(item -> item.getReceipt() != null && item.getReceipt().getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Bon-Position nicht gefunden."));
    }

    private ProductReviewItemDto toDto(ReceiptItem item, long occurrences) {
        Proposal proposal = proposal(item);
        ProductFamily currentFamily = item.getProductFamily();
        ProductVariant currentVariant = item.getProductVariant();
        return new ProductReviewItemDto(
                item.getId(), item.getReceipt().getId(), item.getReceipt().getReceiptDate(),
                item.getReceipt().getStoreName(), item.getReceipt().getStoreBranch(), item.getDescription(),
                item.getQuantity(), item.getUnit(), item.getUnitPrice(), item.getTotalPrice(),
                item.getCategory() == null ? null : item.getCategory().getId(),
                item.getCategory() == null ? null : item.getCategory().getName(),
                currentFamily == null ? null : currentFamily.getId(), currentFamily == null ? null : currentFamily.getName(),
                currentVariant == null ? null : currentVariant.getId(), currentVariant == null ? null : currentVariant.getName(),
                proposal.family() == null ? null : proposal.family().getId(),
                proposal.family() == null ? null : proposal.family().getName(),
                proposal.variant() == null ? null : proposal.variant().getId(),
                proposal.variant() == null ? null : proposal.variant().getName(),
                item.getProductAssignmentSource() == null ? proposal.source() : item.getProductAssignmentSource(),
                item.getProductAssignmentStatus(),
                item.getProductAssignmentConfidence() == null ? proposal.confidence() : item.getProductAssignmentConfidence(),
                proposal.reason(), occurrences);
    }

    private Proposal proposal(ReceiptItem item) {
        if (item.getProductFamily() != null) {
            return new Proposal(item.getProductFamily(), item.getProductVariant(), item.getProductAssignmentSource(),
                    item.getProductAssignmentConfidence(), "CURRENT_ASSIGNMENT");
        }
        return assignmentLogRepository.findFirstByReceiptItem_IdOrderByCreatedAtDesc(item.getId())
                .map(log -> new Proposal(log.getProductFamily(), log.getProductVariant(), log.getSource(),
                        log.getConfidence(), log.getDecisionReason()))
                .orElse(new Proposal(null, null, null, item.getProductAssignmentConfidence(), reviewReason(item)));
    }

    private boolean isReviewCandidate(ProductReviewItemDto item, BigDecimal confidenceMax) {
        return item.assignmentStatus() == ProductAssignmentStatus.NEEDS_REVIEW
                || item.confidence() != null && item.confidence().compareTo(confidenceMax) < 0
                || item.currentProductFamilyId() != null && item.currentProductVariantId() == null;
    }

    private String reviewReason(ReceiptItem item) {
        if (item.getProductAssignmentStatus() == ProductAssignmentStatus.NEEDS_REVIEW) {
            return "NEEDS_REVIEW";
        }
        if (item.getProductAssignmentConfidence() != null
                && item.getProductAssignmentConfidence().compareTo(DEFAULT_REVIEW_CONFIDENCE) < 0) {
            return "LOW_CONFIDENCE";
        }
        return item.getProductFamily() != null && item.getProductVariant() == null
                ? "UNCLEAR_VARIANT"
                : "REVIEW_REQUIRED";
    }

    private boolean validConfidence(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0;
    }

    private String contextKey(ReceiptItem item) {
        String store = item.getReceipt() == null ? "" : item.getReceipt().getStoreName();
        return compact(store) + "|" + compact(item.getDescription());
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record Proposal(
            ProductFamily family,
            ProductVariant variant,
            ProductAssignmentSource source,
            BigDecimal confidence,
            String reason) {
    }
}
