package de.ebon.backup;

import de.ebon.api.service.BackupService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RollingBackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RollingBackupService.class);
    private static final String AUTOMATIC_BACKUP_PREFIX = "ebon-backup-auto-";
    private static final String BACKUP_SUFFIX = ".zip";

    private final BackupService backupService;
    private final RollingBackupProperties properties;

    public RollingBackupService(BackupService backupService, RollingBackupProperties properties) {
        this.backupService = backupService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.backup.rolling.cron:0 0 3 * * *}")
    public void runScheduledBackup() {
        if (!properties.isEnabled()) {
            return;
        }
        createAutomaticBackup();
    }

    public Optional<Path> createAutomaticBackup() {
        try {
            Files.createDirectories(properties.getDirectory());
            BackupService.BackupFile backupFile = backupService.createAutomaticBackup();
            Path target = uniqueTarget(backupFile.filename());
            Files.write(
                    target,
                    backupFile.content(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            applyRetention();
            LOGGER.info("Automatic backup written to {}", target.getFileName());
            return Optional.of(target);
        } catch (BackupRestoreLockedException exception) {
            LOGGER.info("Automatic backup skipped because another backup or restore is running.");
            return Optional.empty();
        } catch (IOException exception) {
            throw new UncheckedIOException("Automatic backup could not be written.", exception);
        }
    }

    public void applyRetention() throws IOException {
        List<Path> automaticBackups;
        try (var stream = Files.list(properties.getDirectory())) {
            automaticBackups = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isAutomaticBackup)
                    .sorted(Comparator
                            .comparing(this::lastModifiedTime)
                            .reversed()
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList();
        }

        for (int index = properties.getRetentionCount(); index < automaticBackups.size(); index++) {
            Files.deleteIfExists(automaticBackups.get(index));
        }
    }

    private Path uniqueTarget(String filename) {
        Path target = properties.getDirectory().resolve(filename);
        if (!Files.exists(target)) {
            return target;
        }
        String baseName = filename.substring(0, filename.length() - BACKUP_SUFFIX.length());
        for (int index = 1; index < 1000; index++) {
            Path candidate = properties.getDirectory().resolve(baseName + "-" + index + BACKUP_SUFFIX);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Kein eindeutiger Backup-Dateiname verfuegbar.");
    }

    private boolean isAutomaticBackup(Path path) {
        String filename = path.getFileName().toString();
        return filename.startsWith(AUTOMATIC_BACKUP_PREFIX) && filename.endsWith(BACKUP_SUFFIX);
    }

    private java.nio.file.attribute.FileTime lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
