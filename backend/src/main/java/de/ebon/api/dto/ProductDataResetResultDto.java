package de.ebon.api.dto;

/**
 * Summarizes the explicit reset of product master data without deleting imported receipts.
 */
public record ProductDataResetResultDto(
        String message,
        long clearedAssignments,
        long deletedAssignmentLogs,
        long deletedProductRules,
        long deletedProductVariants,
        long deletedProductFamilies) {
}
