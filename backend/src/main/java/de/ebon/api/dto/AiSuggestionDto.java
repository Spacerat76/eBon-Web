package de.ebon.api.dto;

import de.ebon.persistence.model.AiCategorizationRejectionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Letzter nicht uebernommener KI-Kategorievorschlag fuer eine unkategorisierte Position.")
public record AiSuggestionDto(
        @Schema(example = "3", nullable = true)
        Long categoryId,
        @Schema(example = "Drogerie", nullable = true)
        String categoryName,
        @Schema(example = "0.820", minimum = "0", maximum = "1", nullable = true)
        BigDecimal confidence,
        @Schema(example = "LOW_CONFIDENCE", allowableValues = {
                "LOW_CONFIDENCE",
                "UNKNOWN_CATEGORY",
                "INVALID_RESPONSE"
        }, nullable = true)
        AiCategorizationRejectionReason rejectionReason) {
}
