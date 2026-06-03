package de.ebon.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SettingsDto(
        @Size(max = 2048)
        String paperlessBaseUrl,
        @Schema(example = "********", description = "Maskiert in Responses; fehlend oder ******** beim Speichern bedeutet unveraendert.")
        @Size(max = 4096)
        String paperlessApiToken,
        @Size(max = 128)
        String paperlessEbonTag,
        @Schema(example = "********", description = "Maskiert in Responses; fehlend oder ******** beim Speichern bedeutet unveraendert.")
        @Size(max = 4096)
        String openRouterApiKey,
        @Size(max = 128)
        String openRouterModel,
        @Schema(example = "0.900", minimum = "0", maximum = "1",
                description = "Minimale KI-Konfidenz fuer automatische Kategorisierung. Default: 0.900.")
        @DecimalMin("0.000")
        @DecimalMax("1.000")
        @Digits(integer = 1, fraction = 3)
        BigDecimal aiCategorizationMinConfidence,
        @Min(1)
        Integer syncIntervalMinutes,
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency) {
}
