package de.ebon.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "System")
public class SystemController {

    @GetMapping("/api/system/ping")
    @Operation(
            summary = "Protected backend smoke endpoint",
            security = @SecurityRequirement(name = "bearerAuth"))
    public Map<String, String> ping() {
        return Map.of("status", "OK");
    }
}

