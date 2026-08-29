package de.ebon.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AuditProductCorrectionRequest(
        @NotNull @Valid AuditExpectedProductAssignment expected,
        Long productFamilyId,
        @Size(max = 255) String newProductFamilyName,
        Long productVariantId,
        @Valid AuditProductVariantRequest newProductVariant,
        @NotNull @DecimalMin("0.980") @DecimalMax("1.000") BigDecimal confidence,
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,64}") String reasonCode) {

    @AssertTrue(message = "Genau eine vorhandene oder neue Produktfamilie muss gesetzt sein.")
    public boolean isFamilyChoiceValid() {
        return (productFamilyId != null) != hasText(newProductFamilyName);
    }

    @AssertTrue(message = "Maximal eine vorhandene oder neue Produktvariante darf gesetzt sein.")
    public boolean isVariantChoiceValid() {
        return productVariantId == null || newProductVariant == null;
    }

    @AssertTrue(message = "Eine vorhandene Produktvariante benötigt eine vorhandene Produktfamilie.")
    public boolean isExistingVariantForExistingFamily() {
        return productVariantId == null || productFamilyId != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
