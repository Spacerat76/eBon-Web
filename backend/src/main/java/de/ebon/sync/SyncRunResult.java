package de.ebon.sync;

import de.ebon.persistence.model.SyncLog;
import de.ebon.persistence.model.SyncStatus;

public record SyncRunResult(
        Long syncLogId,
        SyncStatus status,
        int newDocumentsCount,
        int removedDocumentsCount,
        String errorMessage) {

    static SyncRunResult from(SyncLog syncLog) {
        return new SyncRunResult(
                syncLog.getId(),
                syncLog.getStatus(),
                syncLog.getNewDocumentsCount(),
                syncLog.getRemovedDocumentsCount(),
                syncLog.getErrorMessage());
    }
}
