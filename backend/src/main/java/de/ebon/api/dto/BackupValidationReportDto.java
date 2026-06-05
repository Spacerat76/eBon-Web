package de.ebon.api.dto;

import java.util.List;

public record BackupValidationReportDto(
        boolean valid,
        String manifestVersion,
        List<BackupTableValidationDto> tables,
        List<String> warnings,
        List<String> errors) {
}
