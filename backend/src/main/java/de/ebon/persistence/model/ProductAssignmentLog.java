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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "product_assignment_log")
public class ProductAssignmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_item_id", nullable = false)
    private ReceiptItem receiptItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_family_id")
    private ProductFamily productFamily;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductAssignmentSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductAssignmentStatus status;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "model_used", length = 128)
    private String modelUsed;

    @Column(name = "decision_reason", length = 255)
    private String decisionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ProductAssignmentLog() {
    }

    public ProductAssignmentLog(
            ReceiptItem receiptItem,
            ProductFamily productFamily,
            ProductVariant productVariant,
            ProductAssignmentSource source,
            ProductAssignmentStatus status,
            BigDecimal confidence,
            String modelUsed,
            String decisionReason) {
        this.receiptItem = receiptItem;
        this.productFamily = productFamily;
        this.productVariant = productVariant;
        this.source = source;
        this.status = status;
        this.confidence = confidence;
        this.modelUsed = modelUsed;
        this.decisionReason = decisionReason;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public ProductFamily getProductFamily() {
        return productFamily;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public ProductAssignmentSource getSource() {
        return source;
    }

    public ProductAssignmentStatus getStatus() {
        return status;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
