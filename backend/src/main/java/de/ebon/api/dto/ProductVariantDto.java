package de.ebon.api.dto;

import java.math.BigDecimal;

public record ProductVariantDto(
        Long id,
        Long productFamilyId,
        String productFamilyName,
        String name,
        BigDecimal unitQuantity,
        String unit,
        Integer packageQuantity,
        String packageDescription,
        BigDecimal totalQuantity,
        String totalUnit,
        String gtin,
        boolean isActive,
        long assignedItemsCount) {
}
