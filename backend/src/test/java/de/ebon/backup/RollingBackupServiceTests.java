package de.ebon.backup;

import de.ebon.api.service.BackupService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RollingBackupServiceTests {

    @Mock
    private BackupService backupService;

    @TempDir
    private Path tempDir;

    // Verifies retention deletes only old automatic backups from the configured directory and keeps manual downloads.
    @Test
    void createAutomaticBackupWritesAutoFileAndDeletesOnlyOldAutomaticBackups() throws Exception {
        Path oldAuto1 = writeFile("ebon-backup-auto-2026-06-01_03-00-00-000.zip", Instant.parse("2026-06-01T03:00:00Z"));
        Path oldAuto2 = writeFile("ebon-backup-auto-2026-06-02_03-00-00-000.zip", Instant.parse("2026-06-02T03:00:00Z"));
        Path oldAuto3 = writeFile("ebon-backup-auto-2026-06-03_03-00-00-000.zip", Instant.parse("2026-06-03T03:00:00Z"));
        Path manualBackup = writeFile("ebon-backup-2026-06-01_10-00.zip", Instant.parse("2026-06-01T10:00:00Z"));
        when(backupService.createAutomaticBackup()).thenReturn(new BackupService.BackupFile(
                "ebon-backup-auto-2026-06-04_03-00-00-000.zip",
                "backup".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        RollingBackupService service = new RollingBackupService(backupService, properties(2));

        Optional<Path> created = service.createAutomaticBackup();

        assertThat(created).isPresent();
        assertThat(Files.exists(created.orElseThrow())).isTrue();
        assertThat(Files.exists(manualBackup)).isTrue();
        assertThat(Files.exists(oldAuto3)).isTrue();
        assertThat(Files.exists(oldAuto1)).isFalse();
        assertThat(Files.exists(oldAuto2)).isFalse();
        verify(backupService).createAutomaticBackup();
    }

    // Verifies scheduled automatic backups do not run in parallel with manual backup or restore work.
    @Test
    void createAutomaticBackupSkipsWhenBackupLockIsAlreadyHeld() {
        when(backupService.createAutomaticBackup())
                .thenThrow(new BackupRestoreLockedException("Backup oder Restore laeuft bereits."));
        RollingBackupService service = new RollingBackupService(backupService, properties(7));

        Optional<Path> created = service.createAutomaticBackup();

        assertThat(created).isEmpty();
    }

    private RollingBackupProperties properties(int retentionCount) {
        RollingBackupProperties properties = new RollingBackupProperties();
        properties.setDirectory(tempDir);
        properties.setRetentionCount(retentionCount);
        properties.setEnabled(true);
        return properties;
    }

    private Path writeFile(String filename, Instant modifiedAt) throws Exception {
        Path path = tempDir.resolve(filename);
        Files.writeString(path, "backup");
        Files.setLastModifiedTime(path, FileTime.from(modifiedAt));
        return path;
    }
}
