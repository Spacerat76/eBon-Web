package de.ebon.api.dto;

import java.time.LocalDate;
import java.util.List;

public record ProductChangePreviewDto(
        long affectedItemsCount,
        List<String> affectedStores,
        LocalDate dateFrom,
        LocalDate dateTo,
        Long previousProductFamilyId,
        String previousProductFamilyName,
        Long newProductFamilyId,
        String newProductFamilyName,
        Long previousProductVariantId,
        String previousProductVariantName,
        Long newProductVariantId,
        String newProductVariantName,
        String reportImpact) {
}
