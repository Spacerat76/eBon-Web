package de.ebon.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "sync_log_entry")
public class SyncLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_log_id", nullable = false)
    private SyncLog syncLog;

    @Column(name = "paperless_document_id")
    private Integer paperlessDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SyncLogEntryAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SyncLogEntry() {
    }

    public SyncLogEntry(Integer paperlessDocumentId, SyncLogEntryAction action, Receipt receipt, String details) {
        this.paperlessDocumentId = paperlessDocumentId;
        this.action = action;
        this.receipt = receipt;
        this.details = details;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    void setSyncLog(SyncLog syncLog) {
        this.syncLog = syncLog;
    }

    public Long getId() {
        return id;
    }

    public Integer getPaperlessDocumentId() {
        return paperlessDocumentId;
    }

    public SyncLogEntryAction getAction() {
        return action;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public String getDetails() {
        return details;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
