package de.ebon.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParseRuleSuggestionRejectRequest(
        @NotBlank
        @Size(max = 2048)
        String rejectionReason) {
}
