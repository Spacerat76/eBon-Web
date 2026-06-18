package de.ebon.api.dto;

import jakarta.validation.constraints.NotNull;

public record FixturePreviewRequest(@NotNull Long aiParsingLogId) {
}
