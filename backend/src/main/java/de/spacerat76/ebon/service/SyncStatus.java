package de.spacerat76.ebon.service;

import java.time.OffsetDateTime;

public class SyncStatus {
    private OffsetDateTime lastSyncAt;
    private int lastSyncedCount;
    private int lastErrorCount;
    private long lastDurationMs;

    public SyncStatus() {}

    public SyncStatus(OffsetDateTime lastSyncAt, int lastSyncedCount, int lastErrorCount, long lastDurationMs) {
        this.lastSyncAt = lastSyncAt;
        this.lastSyncedCount = lastSyncedCount;
        this.lastErrorCount = lastErrorCount;
        this.lastDurationMs = lastDurationMs;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(OffsetDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public int getLastSyncedCount() {
        return lastSyncedCount;
    }

    public void setLastSyncedCount(int lastSyncedCount) {
        this.lastSyncedCount = lastSyncedCount;
    }

    public int getLastErrorCount() {
        return lastErrorCount;
    }

    public void setLastErrorCount(int lastErrorCount) {
        this.lastErrorCount = lastErrorCount;
    }

    public long getLastDurationMs() {
        return lastDurationMs;
    }

    public void setLastDurationMs(long lastDurationMs) {
        this.lastDurationMs = lastDurationMs;
    }
}
