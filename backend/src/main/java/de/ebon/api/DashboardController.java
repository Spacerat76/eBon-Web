package de.ebon.api;

import de.ebon.api.dto.DashboardDto;
import de.ebon.api.service.QueryApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final QueryApiService queryApiService;

    public DashboardController(QueryApiService queryApiService) {
        this.queryApiService = queryApiService;
    }

    @GetMapping("/api/dashboard")
    @Operation(summary = "Dashboard-Kennzahlen abrufen")
    public DashboardDto dashboard() {
        return queryApiService.dashboard();
    }
}
