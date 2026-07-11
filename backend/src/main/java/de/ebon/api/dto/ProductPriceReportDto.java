package de.ebon.api.dto;

import java.util.List;

public record ProductPriceReportDto(
        String scope,
        Long productFamilyId,
        String productFamilyName,
        Long productVariantId,
        String productVariantName,
        String primaryPriceBasis,
        List<ProductPriceStatisticsDto> statistics,
        List<ProductPriceStoreDto> stores,
        List<ProductPriceTrendPointDto> trend,
        List<ProductPriceVariantSummaryDto> variants) {
}
