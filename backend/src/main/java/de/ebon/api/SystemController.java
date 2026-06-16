package de.ebon.api;

import de.ebon.api.dto.SystemInfoDto;
import de.ebon.system.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "System")
public class SystemController {

    private final VersionService versionService;

    public SystemController(VersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/api/system/ping")
    @Operation(
            summary = "Protected backend smoke endpoint",
            security = @SecurityRequirement(name = "bearerAuth"))
    public Map<String, String> ping() {
        return Map.of("status", "OK");
    }

    @GetMapping("/api/system/info")
    @Operation(
            summary = "Geschuetzte Build- und Versionsinformationen abrufen",
            security = @SecurityRequirement(name = "bearerAuth"))
    public SystemInfoDto info() {
        return new SystemInfoDto("eBon Expense Tracker", versionService.version());
    }
}
