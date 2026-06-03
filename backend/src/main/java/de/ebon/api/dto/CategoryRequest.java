package de.ebon.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank
        @Size(max = 128)
        String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String colorHex,
        @Size(max = 64)
        String icon,
        Integer sortOrder,
        Boolean isActive) {
}
