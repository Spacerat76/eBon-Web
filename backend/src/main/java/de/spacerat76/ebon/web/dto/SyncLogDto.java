package de.spacerat76.ebon.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class SyncLogDto {
    private Long id;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String status;
    private Integer totalDocuments;
    private Integer succeeded;
    private Integer failed;
    private OffsetDateTime createdAt;
    private List<SyncLogEntryDto> entries;

    public SyncLogDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalDocuments() { return totalDocuments; }
    public void setTotalDocuments(Integer totalDocuments) { this.totalDocuments = totalDocuments; }
    public Integer getSucceeded() { return succeeded; }
    public void setSucceeded(Integer succeeded) { this.succeeded = succeeded; }
    public Integer getFailed() { return failed; }
    public void setFailed(Integer failed) { this.failed = failed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public List<SyncLogEntryDto> getEntries() { return entries; }
    public void setEntries(List<SyncLogEntryDto> entries) { this.entries = entries; }
}
