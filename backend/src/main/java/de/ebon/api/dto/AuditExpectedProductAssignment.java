package de.ebon.api.dto;

import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;

public record AuditExpectedProductAssignment(
        Long productFamilyId,
        Long productVariantId,
        ProductAssignmentSource source,
        ProductAssignmentStatus status) {
}
