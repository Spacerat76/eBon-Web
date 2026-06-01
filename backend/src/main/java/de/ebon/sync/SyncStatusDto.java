package de.ebon.sync;

import de.ebon.persistence.model.SyncLog;
import de.ebon.persistence.model.SyncStatus;
import java.time.OffsetDateTime;
import java.util.Optional;

public record SyncStatusDto(
        OffsetDateTime lastSyncAt,
        SyncStatus lastSyncStatus,
        int newDocumentsCount,
        int removedDocumentsCount,
        int errorCount,
        boolean isSyncing) {

    public static SyncStatusDto from(Optional<SyncLog> syncLog, boolean syncing) {
        return syncLog
                .map(log -> new SyncStatusDto(
                        log.getStartedAt(),
                        log.getStatus(),
                        log.getNewDocumentsCount(),
                        log.getRemovedDocumentsCount(),
                        log.getStatus() == SyncStatus.FAILED ? 1 : 0,
                        syncing))
                .orElseGet(() -> new SyncStatusDto(null, null, 0, 0, 0, syncing));
    }
}
