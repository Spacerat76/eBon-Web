package de.ebon.api.dto;

import de.ebon.persistence.model.CategorySource;
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
        @Schema(nullable = true)
        Long categoryId,
        @Schema(nullable = true, example = "Lebensmittel")
        String categoryName,
        @Schema(nullable = true, allowableValues = {"RULE", "AI", "MANUAL"})
        CategorySource categorySource,
        boolean isManuallyEdited,
        @Schema(nullable = true)
        AiSuggestionDto aiSuggestion) {
}
