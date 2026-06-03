package de.ebon.api.dto;

import de.ebon.persistence.model.CategorySource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ReceiptItemCreateRequest(
        Integer positionIndex,
        @NotBlank
        @Size(max = 512)
        String description,
        @DecimalMin(value = "0.000", inclusive = false)
        @Digits(integer = 7, fraction = 3)
        BigDecimal quantity,
        @Size(max = 32)
        String unit,
        @Digits(integer = 8, fraction = 2)
        BigDecimal unitPrice,
        @NotNull
        @Digits(integer = 8, fraction = 2)
        BigDecimal totalPrice,
        @Digits(integer = 8, fraction = 2)
        BigDecimal discountAmount,
        @Schema(nullable = true)
        Long categoryId,
        @Schema(nullable = true, allowableValues = {"RULE", "AI", "MANUAL"})
        CategorySource categorySource) {

    @AssertTrue(message = "categorySource darf ohne categoryId nicht gesetzt sein.")
    @Schema(hidden = true)
    public boolean isCategoryConsistent() {
        return categoryId != null || categorySource == null;
    }
}
