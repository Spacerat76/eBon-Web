package de.ebon.persistence.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sync_log")
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SyncStatus status = SyncStatus.RUNNING;

    @Column(name = "new_documents_count", nullable = false)
    private int newDocumentsCount;

    @Column(name = "removed_documents_count", nullable = false)
    private int removedDocumentsCount;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @OneToMany(mappedBy = "syncLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SyncLogEntry> entries = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public void addEntry(SyncLogEntry entry) {
        entries.add(entry);
        entry.setSyncLog(this);
    }

    public void markSuccess(int newDocumentsCount, int removedDocumentsCount) {
        this.status = SyncStatus.SUCCESS;
        this.newDocumentsCount = newDocumentsCount;
        this.removedDocumentsCount = removedDocumentsCount;
        this.finishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = SyncStatus.FAILED;
        this.finishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public int getNewDocumentsCount() {
        return newDocumentsCount;
    }

    public int getRemovedDocumentsCount() {
        return removedDocumentsCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<SyncLogEntry> getEntries() {
        return List.copyOf(entries);
    }
}
