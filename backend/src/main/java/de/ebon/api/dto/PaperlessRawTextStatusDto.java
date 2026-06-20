package de.ebon.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datensparsamer Vergleichsstatus zwischen gespeichertem und aktuellem Paperless-Rohtext.")
public record PaperlessRawTextStatusDto(
        @Schema(description = "Kein Rohtext oder Hash wird in dieser Antwort uebertragen.", example = "CHANGED")
        PaperlessRawTextStatus status) {
}
