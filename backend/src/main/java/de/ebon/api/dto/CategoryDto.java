package de.ebon.api.dto;

public record CategoryDto(
        Long id,
        String name,
        String colorHex,
        String icon,
        boolean isActive,
        int sortOrder,
        long assignedItemsCount) {
}
