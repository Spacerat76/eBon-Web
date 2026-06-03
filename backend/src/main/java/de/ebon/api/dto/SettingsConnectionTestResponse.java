package de.ebon.api.dto;

public record SettingsConnectionTestResponse(
        String target,
        boolean success,
        String message) {
}
