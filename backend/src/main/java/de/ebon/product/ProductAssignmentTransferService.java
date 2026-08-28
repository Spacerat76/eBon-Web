package de.ebon.product;

import de.ebon.persistence.model.ProductAssignmentLog;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.ExtractionStatus;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Transfers only explicit product confirmations across a single-receipt reparse.
 * Any plausible but changed item is left for review instead of guessing a variant.
 */
@Service
public class ProductAssignmentTransferService {

    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.02");

    private final ProductAssignmentLogRepository assignmentLogRepository;

    public ProductAssignmentTransferService(ProductAssignmentLogRepository assignmentLogRepository) {
        this.assignmentLogRepository = assignmentLogRepository;
    }

    public void transferConfirmedAssignments(List<ReceiptItem> previousItems, List<ReceiptItem> reparsedItems) {
        List<ReceiptItem> availableItems = new ArrayList<>(reparsedItems.stream()
                .filter(item -> item.getExtractionStatus() == ExtractionStatus.CONFIRMED).toList());
        for (ReceiptItem previous : previousItems) {
            if (!isConfirmedManualAssignment(previous)) {
                continue;
            }

            List<ReceiptItem> candidates = availableItems.stream()
                    .filter(candidate -> compact(candidate.getDescription()).equals(compact(previous.getDescription())))
                    .toList();
            if (candidates.size() != 1) {
                candidates.forEach(candidate -> markConflict(candidate, previous, "REPARSE_ASSIGNMENT_AMBIGUOUS"));
                availableItems.removeAll(candidates);
                continue;
            }

            ReceiptItem candidate = candidates.getFirst();
            availableItems.remove(candidate);
            if (plausiblySameItem(previous, candidate)) {
                candidate.assignProduct(
                        previous.getProductFamily(),
                        previous.getProductVariant(),
                        ProductAssignmentSource.MANUAL,
                        ProductAssignmentStatus.CONFIRMED,
                        null);
                assignmentLogRepository.save(new ProductAssignmentLog(
                        candidate,
                        previous.getProductFamily(),
                        previous.getProductVariant(),
                        ProductAssignmentSource.MANUAL,
                        ProductAssignmentStatus.CONFIRMED,
                        null,
                        "reparse-transfer",
                        "REPARSE_CONFIRMED_TRANSFER"));
            } else {
                markConflict(candidate, previous, "REPARSE_ASSIGNMENT_CONFLICT");
            }
        }
    }

    private boolean isConfirmedManualAssignment(ReceiptItem item) {
        return item.getProductFamily() != null
                && item.getProductAssignmentSource() == ProductAssignmentSource.MANUAL
                && item.getProductAssignmentStatus() == ProductAssignmentStatus.CONFIRMED;
    }

    private boolean plausiblySameItem(ReceiptItem previous, ReceiptItem candidate) {
        return sameQuantity(previous, candidate) && samePrice(previous.getTotalPrice(), candidate.getTotalPrice());
    }

    private boolean sameQuantity(ReceiptItem previous, ReceiptItem candidate) {
        if (previous.getQuantity() == null || candidate.getQuantity() == null) {
            return previous.getQuantity() == null && candidate.getQuantity() == null;
        }
        return previous.getQuantity().compareTo(candidate.getQuantity()) == 0
                && normalizeUnit(previous.getUnit()).equals(normalizeUnit(candidate.getUnit()));
    }

    private boolean samePrice(BigDecimal previous, BigDecimal candidate) {
        if (previous == null || candidate == null) {
            return previous == null && candidate == null;
        }
        return previous.subtract(candidate).abs().compareTo(PRICE_TOLERANCE) <= 0;
    }

    private void markConflict(ReceiptItem candidate, ReceiptItem previous, String reason) {
        candidate.markProductNeedsReview(null);
        assignmentLogRepository.save(new ProductAssignmentLog(
                candidate,
                previous.getProductFamily(),
                previous.getProductVariant(),
                ProductAssignmentSource.MANUAL,
                ProductAssignmentStatus.NEEDS_REVIEW,
                null,
                "reparse-transfer",
                reason));
    }

    private String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String normalizeUnit(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
