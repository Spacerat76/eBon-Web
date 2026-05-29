package de.spacerat76.ebon.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sync_log_entry")
public class SyncLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_log_id")
    private SyncLog syncLog;

    @Column(name = "paperless_document_id")
    private Integer paperlessDocumentId;

    @Column(name = "action")
    private String action;

    @Column(name = "message")
    private String message;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SyncLog getSyncLog() { return syncLog; }
    public void setSyncLog(SyncLog syncLog) { this.syncLog = syncLog; }
    public Integer getPaperlessDocumentId() { return paperlessDocumentId; }
    public void setPaperlessDocumentId(Integer paperlessDocumentId) { this.paperlessDocumentId = paperlessDocumentId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
