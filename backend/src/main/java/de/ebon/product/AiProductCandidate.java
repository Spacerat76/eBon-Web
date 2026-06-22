package de.ebon.product;

public record AiProductCandidate(
        Long productFamilyId,
        String productFamilyName,
        Long productVariantId,
        String productVariantName) {
}
