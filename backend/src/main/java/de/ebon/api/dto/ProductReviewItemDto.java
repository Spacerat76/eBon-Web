package de.ebon.api.dto;

import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductReviewItemDto(
        Long receiptItemId,
        Long receiptId,
        LocalDate receiptDate,
        String storeName,
        String storeBranch,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Long categoryId,
        String categoryName,
        Long currentProductFamilyId,
        String currentProductFamilyName,
        Long currentProductVariantId,
        String currentProductVariantName,
        Long suggestedProductFamilyId,
        String suggestedProductFamilyName,
        Long suggestedProductVariantId,
        String suggestedProductVariantName,
        ProductAssignmentSource assignmentSource,
        ProductAssignmentStatus assignmentStatus,
        BigDecimal confidence,
        String reason,
        long possibleRetroactiveItems) {
}
