package de.ebon.api;

import de.ebon.api.dto.PageResponse;
import de.ebon.persistence.repository.SyncLogRepository;
import de.ebon.sync.PaperlessSyncService;
import de.ebon.sync.SyncAlreadyRunningException;
import de.ebon.sync.SyncLogDto;
import de.ebon.sync.SyncStatusDto;
import de.ebon.sync.SyncTriggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Sync")
public class SyncController {

    private final PaperlessSyncService syncService;
    private final SyncLogRepository syncLogRepository;

    public SyncController(PaperlessSyncService syncService, SyncLogRepository syncLogRepository) {
        this.syncService = syncService;
        this.syncLogRepository = syncLogRepository;
    }

    @PostMapping("/api/sync/trigger")
    @Operation(summary = "Start Paperless-NGX sync", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> triggerSync() {
        try {
            syncService.triggerAsync();
            return ResponseEntity
                    .accepted()
                    .body(new SyncTriggerResponse("Sync gestartet"));
        } catch (SyncAlreadyRunningException exception) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(syncService.currentStatus());
        }
    }

    @GetMapping("/api/sync/status")
    @Operation(summary = "Get latest sync status", security = @SecurityRequirement(name = "bearerAuth"))
    public SyncStatusDto syncStatus() {
        return syncService.currentStatus();
    }

    @GetMapping("/api/sync/log")
    @Operation(summary = "Get sync log", security = @SecurityRequirement(name = "bearerAuth"))
    public PageResponse<SyncLogDto> syncLog(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(syncLogRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size))
                .map(SyncLogDto::from), "startedAt", "desc");
    }
}
