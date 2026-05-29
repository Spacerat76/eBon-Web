package de.spacerat76.ebon.web;

import de.spacerat76.ebon.service.PaperlessSyncService;
import de.spacerat76.ebon.service.SyncStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final PaperlessSyncService syncService;

    public SyncController(PaperlessSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public ResponseEntity<Void> triggerFullSync() {
        syncService.syncNewDocuments();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/document/{id}")
    public ResponseEntity<Void> syncDocument(@PathVariable Integer id) {
        boolean ok = syncService.syncDocument(id);
        if (ok) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(500).build();
    }

    @GetMapping("/status")
    public ResponseEntity<SyncStatus> status() {
        return ResponseEntity.ok(syncService.getStatus());
    }
}
