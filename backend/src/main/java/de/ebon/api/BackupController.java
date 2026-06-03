package de.ebon.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "Backup & Restore")
@SecurityRequirement(name = "bearerAuth")
public class BackupController {

    @GetMapping("/api/backup/download")
    @Operation(summary = "Backup-ZIP herunterladen (Phase 9)")
    public void downloadBackup() {
        throw notImplemented();
    }

    @PostMapping("/api/backup/restore")
    @Operation(summary = "Backup wiederherstellen (Phase 9)")
    public void restoreBackup(@RequestParam("file") MultipartFile file) {
        throw notImplemented();
    }

    @PostMapping("/api/backup/validate")
    @Operation(summary = "Backup-ZIP dry-run validieren (Phase 9)")
    public void validateBackup(@RequestParam("file") MultipartFile file) {
        throw notImplemented();
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Backup und Restore werden in Phase 9 implementiert.");
    }
}
