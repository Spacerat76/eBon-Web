package de.ebon.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductPriceExclusionRequest(
        @Schema(example = "Offensichtlicher Erfassungsfehler")
        @NotBlank(message = "Ein Ausschluss braucht eine Begründung.")
        @Size(max = 200, message = "Die Ausschlussbegründung darf höchstens 200 Zeichen enthalten.")
        String reason) {
}
