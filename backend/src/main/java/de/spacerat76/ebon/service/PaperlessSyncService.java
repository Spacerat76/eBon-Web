package de.spacerat76.ebon.service;

import java.time.OffsetDateTime;

public interface PaperlessSyncService {
    void syncNewDocuments();

    /**
     * Sync a single Paperless document by id. Returns true when synced successfully.
     */
    boolean syncDocument(Integer documentId);

    /**
     * Returns basic sync status information.
     */
    SyncStatus getStatus();
}
