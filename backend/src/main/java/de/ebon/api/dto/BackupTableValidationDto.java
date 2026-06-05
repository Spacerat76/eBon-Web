package de.ebon.api.dto;

public record BackupTableValidationDto(
        String name,
        long recordCount,
        boolean valid) {
}
