package de.ebon.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MessageResponse(
        @Schema(example = "OK")
        String message) {
}
