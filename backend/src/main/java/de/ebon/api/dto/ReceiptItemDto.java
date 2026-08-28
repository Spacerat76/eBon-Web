package de.ebon.api.dto;

import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.ExtractionStatus;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Bon-Position. Ohne Kategorie wird als categoryId=null und categorySource=null abgebildet.")
public record ReceiptItemDto(
        Long id,
        Long receiptId,
        int positionIndex,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal discountAmount,
        ExtractionStatus extractionStatus,
        @Schema(nullable = true)
        Long categoryId,
        @Schema(nullable = true, example = "Lebensmittel")
        String categoryName,
        @Schema(nullable = true, allowableValues = {"RULE", "AI", "MANUAL"})
        CategorySource categorySource,
        boolean isManuallyEdited,
        @Schema(nullable = true)
        AiSuggestionDto aiSuggestion,
        Long productFamilyId,
        String productFamilyName,
        Long productVariantId,
        String productVariantName,
        ProductAssignmentSource productAssignmentSource,
        ProductAssignmentStatus productAssignmentStatus,
        BigDecimal productAssignmentConfidence,
        BigDecimal computedUnitPrice,
        String computedUnitPriceUnit,
        boolean excludeFromProductPriceComparison,
        String productPriceExclusionReason) {
}
