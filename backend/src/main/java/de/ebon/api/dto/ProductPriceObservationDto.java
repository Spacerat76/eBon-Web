package de.ebon.api.dto;

import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductPriceObservationDto(
        Long receiptItemId,
        Long receiptId,
        LocalDate receiptDate,
        String storeName,
        String storeBranch,
        String description,
        Long productFamilyId,
        String productFamilyName,
        Long productVariantId,
        String productVariantName,
        ProductAssignmentSource assignmentSource,
        ProductAssignmentStatus assignmentStatus,
        BigDecimal effectivePrice,
        BigDecimal regularPrice,
        BigDecimal normalizedUnitPrice,
        String normalizedUnit,
        boolean includedInComparison,
        boolean outlier,
        boolean excluded,
        String exclusionReason) {
}
