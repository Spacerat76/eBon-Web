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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_parsing_log")
public class AiParsingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiParsingTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiParsingStatus status;

    @Column(name = "model_used", length = 128)
    private String modelUsed;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "parse_error_before", columnDefinition = "text")
    private String parseErrorBefore;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "overall_confidence", precision = 4, scale = 3)
    private BigDecimal overallConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_confidence_json", columnDefinition = "jsonb")
    private String fieldConfidenceJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings_json", columnDefinition = "jsonb")
    private String warningsJson;

    @Column(name = "prompt_snippet", columnDefinition = "text")
    private String promptSnippet;

    @Column(name = "response_snippet", columnDefinition = "text")
    private String responseSnippet;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AiParsingLog() {
    }

    public AiParsingLog(Receipt receipt, AiParsingTrigger trigger, String parseErrorBefore) {
        this.receipt = receipt;
        this.trigger = trigger;
        this.parseErrorBefore = parseErrorBefore;
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (startedAt == null) {
            startedAt = createdAt;
        }
    }

    public void finish(
            AiParsingStatus status,
            String modelUsed,
            String failureReason,
            BigDecimal overallConfidence,
            String fieldConfidenceJson,
            String warningsJson,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String promptSnippet,
            String responseSnippet) {
        this.status = status;
        this.modelUsed = modelUsed;
        this.failureReason = failureReason;
        this.overallConfidence = overallConfidence;
        this.fieldConfidenceJson = fieldConfidenceJson;
        this.warningsJson = warningsJson;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.promptSnippet = promptSnippet;
        this.responseSnippet = responseSnippet;
        this.finishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.durationMs = Math.toIntExact(Duration.between(startedAt, finishedAt).toMillis());
    }

    public Long getId() {
        return id;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public AiParsingTrigger getTrigger() {
        return trigger;
    }

    public AiParsingStatus getStatus() {
        return status;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public String getParseErrorBefore() {
        return parseErrorBefore;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public BigDecimal getOverallConfidence() {
        return overallConfidence;
    }

    public String getFieldConfidenceJson() {
        return fieldConfidenceJson;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public String getPromptSnippet() {
        return promptSnippet;
    }

    public String getResponseSnippet() {
        return responseSnippet;
    }
}
