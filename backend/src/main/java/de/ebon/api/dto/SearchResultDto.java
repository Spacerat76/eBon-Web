package de.ebon.api.dto;

import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SearchResultDto(
        Long receiptId,
        Long receiptItemId,
        LocalDate receiptDate,
        String storeName,
        String description,
        BigDecimal totalPrice,
        Long categoryId,
        String categoryName,
        List<String> highlights,
        Long productFamilyId,
        String productFamilyName,
        Long productVariantId,
        String productVariantName,
        ProductAssignmentSource productAssignmentSource,
        ProductAssignmentStatus productAssignmentStatus,
        BigDecimal normalizedUnitPrice,
        String normalizedUnit) {

    public SearchResultDto(
            Long receiptId,
            Long receiptItemId,
            LocalDate receiptDate,
            String storeName,
            String description,
            BigDecimal totalPrice,
            Long categoryId,
            String categoryName,
            List<String> highlights) {
        this(
                receiptId,
                receiptItemId,
                receiptDate,
                storeName,
                description,
                totalPrice,
                categoryId,
                categoryName,
                highlights,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
