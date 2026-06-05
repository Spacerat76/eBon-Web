package de.ebon.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DataMaintenanceResetRequest(
        @NotBlank
        String confirmation) {
}
