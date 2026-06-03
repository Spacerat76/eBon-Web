package de.ebon.api.dto;

import jakarta.validation.constraints.NotNull;

public record SettingsConnectionTestRequest(@NotNull Target target) {

    public enum Target {
        PAPERLESS,
        OPENROUTER
    }
}
