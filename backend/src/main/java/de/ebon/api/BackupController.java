package de.ebon.api;

import de.ebon.api.dto.BackupRestoreResultDto;
import de.ebon.api.dto.BackupValidationReportDto;
import de.ebon.api.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Backup & Restore")
@SecurityRequirement(name = "bearerAuth")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping(value = "/api/backup/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Backup-ZIP herunterladen")
    public ResponseEntity<byte[]> downloadBackup() {
        BackupService.BackupFile backupFile = backupService.createBackup();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(backupFile.filename())
                        .build()
                        .toString())
                .body(backupFile.content());
    }

    @PostMapping(value = "/api/backup/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Backup wiederherstellen")
    public BackupRestoreResultDto restoreBackup(@RequestParam("file") MultipartFile file) {
        return backupService.restore(file);
    }

    @PostMapping(value = "/api/backup/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Backup-ZIP dry-run validieren")
    public BackupValidationReportDto validateBackup(@RequestParam("file") MultipartFile file) {
        return backupService.validate(file);
    }
}
