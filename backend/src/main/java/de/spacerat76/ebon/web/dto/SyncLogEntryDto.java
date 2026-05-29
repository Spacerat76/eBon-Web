package de.spacerat76.ebon.web.dto;

import java.time.OffsetDateTime;

public class SyncLogEntryDto {
    private Long id;
    private Long syncLogId;
    private Integer paperlessDocumentId;
    private String action;
    private String message;
    private OffsetDateTime createdAt;

    public SyncLogEntryDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSyncLogId() { return syncLogId; }
    public void setSyncLogId(Long syncLogId) { this.syncLogId = syncLogId; }
    public Integer getPaperlessDocumentId() { return paperlessDocumentId; }
    public void setPaperlessDocumentId(Integer paperlessDocumentId) { this.paperlessDocumentId = paperlessDocumentId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
