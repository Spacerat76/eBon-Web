package de.ebon.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductVariantRequest(
        @NotNull
        Long productFamilyId,
        @NotBlank
        @Size(max = 255)
        String name,
        @DecimalMin(value = "0.000", inclusive = false)
        @Digits(integer = 9, fraction = 3)
        BigDecimal unitQuantity,
        @Size(max = 32)
        String unit,
        @Min(1)
        Integer packageQuantity,
        @Size(max = 255)
        String packageDescription,
        @DecimalMin(value = "0.000", inclusive = false)
        @Digits(integer = 9, fraction = 3)
        BigDecimal totalQuantity,
        @Size(max = 32)
        String totalUnit,
        @Size(max = 32)
        String gtin,
        Boolean isActive) {
}
