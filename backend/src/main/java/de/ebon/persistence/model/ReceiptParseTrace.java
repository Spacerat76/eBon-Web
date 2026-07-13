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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "receipt_parse_trace")
public class ReceiptParseTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "format_profile_id")
    private ReceiptFormatProfile formatProfile;

    @Column(name = "format_profile_version")
    private Integer formatProfileVersion;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 32)
    private ParseLineType lineType;

    @Column(name = "position_index")
    private Integer positionIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields", nullable = false, columnDefinition = "jsonb")
    private String extractedFields;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ReceiptParseTrace() {
    }

    public ReceiptParseTrace(
            Receipt receipt,
            ReceiptFormatProfile formatProfile,
            int lineNumber,
            ParseLineType lineType,
            Integer positionIndex,
            String extractedFields,
            String reason,
            boolean needsReview) {
        this.receipt = receipt;
        this.formatProfile = formatProfile;
        this.formatProfileVersion = formatProfile == null ? null : formatProfile.getVersion();
        this.lineNumber = lineNumber;
        this.lineType = lineType;
        this.positionIndex = positionIndex;
        this.extractedFields = extractedFields == null ? "{}" : extractedFields;
        this.reason = reason;
        this.needsReview = needsReview;
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

    public Receipt getReceipt() {
        return receipt;
    }

    public ReceiptFormatProfile getFormatProfile() {
        return formatProfile;
    }

    public Integer getFormatProfileVersion() {
        return formatProfileVersion;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public ParseLineType getLineType() {
        return lineType;
    }

    public Integer getPositionIndex() {
        return positionIndex;
    }

    public String getExtractedFields() {
        return extractedFields;
    }

    public String getReason() {
        return reason;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }
}
