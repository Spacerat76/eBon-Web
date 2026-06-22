package de.ebon.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductFamilyRequest(
        @NotBlank
        @Size(max = 255)
        String name,
        Long defaultCategoryId,
        Boolean isActive) {
}
