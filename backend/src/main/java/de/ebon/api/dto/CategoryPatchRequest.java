package de.ebon.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryPatchRequest(
        @Size(min = 1, max = 128)
        String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String colorHex,
        @Schema(description = "Optionaler Wert aus GET /api/categories/icons; kein Freitext.")
        @Size(max = 64)
        String icon,
        Integer sortOrder,
        Boolean isActive) {
}
