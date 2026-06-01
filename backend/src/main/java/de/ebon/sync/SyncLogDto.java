package de.ebon.sync;

import de.ebon.persistence.model.SyncLog;
import de.ebon.persistence.model.SyncStatus;
import java.time.OffsetDateTime;

public record SyncLogDto(
        Long id,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        SyncStatus status,
        int newDocumentsCount,
        int removedDocumentsCount,
        String errorMessage) {

    public static SyncLogDto from(SyncLog syncLog) {
        return new SyncLogDto(
                syncLog.getId(),
                syncLog.getStartedAt(),
                syncLog.getFinishedAt(),
                syncLog.getStatus(),
                syncLog.getNewDocumentsCount(),
                syncLog.getRemovedDocumentsCount(),
                syncLog.getErrorMessage());
    }
}
