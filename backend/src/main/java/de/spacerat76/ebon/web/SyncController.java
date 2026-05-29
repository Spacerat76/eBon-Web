package de.spacerat76.ebon.web;

import de.spacerat76.ebon.service.PaperlessSyncService;
import de.spacerat76.ebon.service.SyncStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Sync", description = "Paperless synchronization endpoints")
public class SyncController {

    private final PaperlessSyncService syncService;

    public SyncController(PaperlessSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    @Operation(summary = "Trigger full sync of new Paperless documents")
    public ResponseEntity<Void> triggerFullSync() {
        syncService.syncNewDocuments();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/document/{id}")
    @Operation(summary = "Sync a single Paperless document by id")
    public ResponseEntity<Void> syncDocument(@PathVariable Integer id) {
        boolean ok = syncService.syncDocument(id);
        if (ok) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(500).build();
    }

    @GetMapping("/status")
    @Operation(summary = "Get last sync status")
    public ResponseEntity<SyncStatus> status() {
        return ResponseEntity.ok(syncService.getStatus());
    }
}
