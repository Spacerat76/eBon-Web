package de.ebon.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "ai_categorization_log")
public class AiCategorizationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_item_id", nullable = false)
    private ReceiptItem receiptItem;

    @Column(name = "prompt_sent", nullable = false, columnDefinition = "text")
    private String promptSent;

    @Column(name = "response_received", nullable = false, columnDefinition = "text")
    private String responseReceived;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_category_id")
    private Category assignedCategory;

    @Column(name = "ai_confidence", precision = 4, scale = 3)
    private BigDecimal aiConfidence;

    @Column(name = "model_used", nullable = false, length = 128)
    private String modelUsed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AiCategorizationLog() {
    }

    public AiCategorizationLog(ReceiptItem receiptItem, String promptSent, String responseReceived, String modelUsed) {
        this.receiptItem = receiptItem;
        this.promptSent = promptSent;
        this.responseReceived = responseReceived;
        this.modelUsed = modelUsed;
    }

    public AiCategorizationLog(
            ReceiptItem receiptItem,
            String promptSent,
            String responseReceived,
            Category assignedCategory,
            BigDecimal aiConfidence,
            String modelUsed) {
        this.receiptItem = receiptItem;
        this.promptSent = promptSent;
        this.responseReceived = responseReceived;
        this.assignedCategory = assignedCategory;
        this.aiConfidence = aiConfidence;
        this.modelUsed = modelUsed;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public ReceiptItem getReceiptItem() {
        return receiptItem;
    }

    public Category getAssignedCategory() {
        return assignedCategory;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public String getModelUsed() {
        return modelUsed;
    }
}
