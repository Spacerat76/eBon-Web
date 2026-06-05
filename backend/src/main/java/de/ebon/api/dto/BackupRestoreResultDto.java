package de.ebon.api.dto;

public record BackupRestoreResultDto(
        String message,
        BackupValidationReportDto validation) {
}
