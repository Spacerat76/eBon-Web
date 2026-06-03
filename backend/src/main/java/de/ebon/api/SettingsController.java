package de.ebon.api;

import de.ebon.api.dto.SettingsConnectionTestRequest;
import de.ebon.api.dto.SettingsConnectionTestResponse;
import de.ebon.api.dto.SettingsDto;
import de.ebon.api.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Einstellungen")
@SecurityRequirement(name = "bearerAuth")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/api/settings")
    @Operation(summary = "Aktuelle Einstellungen maskiert abrufen")
    public SettingsDto getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping("/api/settings")
    @Operation(summary = "Einstellungen speichern; ******** wird nie persistiert")
    public SettingsDto updateSettings(@Valid @RequestBody SettingsDto request) {
        return settingsService.update(request);
    }

    @PostMapping("/api/settings/test-connection")
    @Operation(summary = "Verbindungstest vorbereiten")
    public SettingsConnectionTestResponse testConnection(
            @Valid @RequestBody SettingsConnectionTestRequest request) {
        return settingsService.testConnection(request);
    }
}
