package de.ebon.sync;

public class SyncAlreadyRunningException extends RuntimeException {

    public SyncAlreadyRunningException() {
        super("Ein Sync-Lauf ist bereits aktiv.");
    }
}
