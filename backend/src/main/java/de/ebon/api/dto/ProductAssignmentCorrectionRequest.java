package de.ebon.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record ProductAssignmentCorrectionRequest(
        Long productFamilyId,
        @Size(max = 255)
        String newProductFamilyName,
        Long productVariantId,
        Boolean applyToSameStoreDescription) {

    @AssertTrue(message = "Entweder productFamilyId oder newProductFamilyName muss gesetzt sein.")
    public boolean isProductFamilyDefined() {
        return productFamilyId != null || newProductFamilyName != null && !newProductFamilyName.isBlank();
    }

    @AssertTrue(message = "productVariantId kann nur mit einer vorhandenen productFamilyId gesetzt werden.")
    public boolean isVariantOnlyForExistingFamily() {
        return productVariantId == null || productFamilyId != null;
    }
}
