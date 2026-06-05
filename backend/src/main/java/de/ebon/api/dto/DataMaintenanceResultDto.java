package de.ebon.api.dto;

public record DataMaintenanceResultDto(
        String message,
        long totalReceipts,
        long processedReceipts,
        long skippedManualReceipts,
        long deletedReceipts,
        long deletedSyncLogs) {
}
