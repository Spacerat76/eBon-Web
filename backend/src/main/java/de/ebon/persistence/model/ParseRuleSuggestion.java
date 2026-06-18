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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "parse_rule_suggestion")
public class ParseRuleSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_parsing_log_id", nullable = false)
    private AiParsingLog aiParsingLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @Column(name = "store_name", length = 255)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 32)
    private ParseRuleType ruleType;

    @Column(name = "match_regex", nullable = false, length = 1024)
    private String matchRegex;

    @Column(name = "extract_group", length = 64)
    private String extractGroup;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiParsingTrigger trigger;

    @Column(name = "problem_description", nullable = false, columnDefinition = "text")
    private String problemDescription;

    @Column(name = "solution_rationale", nullable = false, columnDefinition = "text")
    private String solutionRationale;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 32)
    private ParseRuleValidationStatus validationStatus;

    @Column(name = "validation_message", columnDefinition = "text")
    private String validationMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParseRuleSuggestionStatus status = ParseRuleSuggestionStatus.OPEN;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_parse_rule_id")
    private ParseRule acceptedParseRule;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ParseRuleSuggestion() {
    }

    public ParseRuleSuggestion(
            AiParsingLog aiParsingLog,
            Receipt receipt,
            String storeName,
            ParseRuleType ruleType,
            String matchRegex,
            String extractGroup,
            BigDecimal confidence,
            AiParsingTrigger trigger,
            String problemDescription,
            String solutionRationale,
            ParseRuleValidationStatus validationStatus,
            String validationMessage) {
        this.aiParsingLog = aiParsingLog;
        this.receipt = receipt;
        this.storeName = storeName;
        this.ruleType = ruleType;
        this.matchRegex = matchRegex;
        this.extractGroup = extractGroup;
        this.confidence = confidence;
        this.trigger = trigger;
        this.problemDescription = problemDescription;
        this.solutionRationale = solutionRationale;
        this.validationStatus = validationStatus;
        this.validationMessage = validationMessage;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void updateDraft(
            String storeName,
            ParseRuleType ruleType,
            String matchRegex,
            String extractGroup,
            BigDecimal confidence,
            String problemDescription,
            String solutionRationale,
            ParseRuleValidationStatus validationStatus,
            String validationMessage) {
        this.storeName = storeName;
        this.ruleType = ruleType;
        this.matchRegex = matchRegex;
        this.extractGroup = extractGroup;
        this.confidence = confidence;
        this.problemDescription = problemDescription;
        this.solutionRationale = solutionRationale;
        this.validationStatus = validationStatus;
        this.validationMessage = validationMessage;
    }

    public void accept(ParseRule parseRule) {
        this.status = ParseRuleSuggestionStatus.ACCEPTED;
        this.acceptedParseRule = parseRule;
        this.rejectionReason = null;
    }

    public void reject(String rejectionReason) {
        this.status = ParseRuleSuggestionStatus.REJECTED;
        this.rejectionReason = rejectionReason;
    }

    public Long getId() {
        return id;
    }

    public AiParsingLog getAiParsingLog() {
        return aiParsingLog;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public String getStoreName() {
        return storeName;
    }

    public ParseRuleType getRuleType() {
        return ruleType;
    }

    public String getMatchRegex() {
        return matchRegex;
    }

    public String getExtractGroup() {
        return extractGroup;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public AiParsingTrigger getTrigger() {
        return trigger;
    }

    public String getProblemDescription() {
        return problemDescription;
    }

    public String getSolutionRationale() {
        return solutionRationale;
    }

    public ParseRuleValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public ParseRuleSuggestionStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public ParseRule getAcceptedParseRule() {
        return acceptedParseRule;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
