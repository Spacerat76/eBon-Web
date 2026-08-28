package de.ebon.api.dto;

import de.ebon.persistence.model.ParseLineType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sichere Parser-Zeilenspur mit einem Auszug aus dem gespeicherten Bontext.")
public record ParseTraceLineDto(
        int lineNumber,
        @Schema(nullable = true, description = "Nur die referenzierte Zeile aus dem gespeicherten Bontext.")
        String lineText,
        ParseLineType lineType,
        @Schema(nullable = true)
        Integer positionIndex,
        String reason,
        boolean needsReview,
        @Schema(nullable = true)
        Long formatProfileId,
        @Schema(nullable = true)
        Integer formatProfileVersion) {
}
