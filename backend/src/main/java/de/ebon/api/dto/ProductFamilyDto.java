package de.ebon.api.dto;

import java.time.OffsetDateTime;

public record ProductFamilyDto(
        Long id,
        String name,
        Long defaultCategoryId,
        String defaultCategoryName,
        boolean isActive,
        long variantCount,
        long assignedItemsCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
