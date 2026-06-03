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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "receipt")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paperless_document_id", nullable = false, unique = true)
    private Integer paperlessDocumentId;

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Column(name = "receipt_time")
    private LocalTime receiptTime;

    @Column(name = "store_name", length = 255)
    private String storeName;

    @Column(name = "store_branch", length = 255)
    private String storeBranch;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, columnDefinition = "char(3)", length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency = "EUR";

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    @Column(name = "bonus_balance", precision = 10, scale = 2)
    private BigDecimal bonusBalance;

    @Column(name = "bonus_points", precision = 10, scale = 2)
    private BigDecimal bonusPoints;

    @Column(name = "bonus_type", length = 64)
    private String bonusType;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 32)
    private ParseStatus parseStatus = ParseStatus.PENDING;

    @Column(name = "parse_error_message", columnDefinition = "text")
    private String parseErrorMessage;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delete_reason", length = 32)
    private DeleteReason deleteReason;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptItem> items = new ArrayList<>();

    protected Receipt() {
    }

    public Receipt(Integer paperlessDocumentId, String rawText) {
        this.paperlessDocumentId = paperlessDocumentId;
        this.rawText = rawText;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (importedAt == null) {
            importedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void addItem(ReceiptItem item) {
        items.add(item);
        item.setReceipt(this);
    }

    public void clearItems() {
        items.clear();
    }

    public void applyParseResult(
            ParseStatus parseStatus,
            String parseErrorMessage,
            LocalDate receiptDate,
            LocalTime receiptTime,
            String storeName,
            String storeBranch,
            BigDecimal totalAmount,
            String currency,
            BigDecimal bonusBalance,
            BigDecimal bonusPoints,
            String bonusType) {
        this.parseStatus = parseStatus;
        this.parseErrorMessage = parseErrorMessage;
        this.receiptDate = receiptDate;
        this.receiptTime = receiptTime;
        this.storeName = storeName;
        this.storeBranch = storeBranch;
        this.totalAmount = totalAmount;
        this.currency = currency == null || currency.isBlank() ? "EUR" : currency;
        this.bonusBalance = bonusBalance;
        this.bonusPoints = bonusPoints;
        this.bonusType = bonusType;
    }

    public void updateManualValues(
            LocalDate receiptDate,
            LocalTime receiptTime,
            String storeName,
            String storeBranch,
            BigDecimal totalAmount,
            String currency,
            BigDecimal bonusBalance,
            BigDecimal bonusPoints,
            String bonusType) {
        this.receiptDate = receiptDate;
        this.receiptTime = receiptTime;
        this.storeName = storeName;
        this.storeBranch = storeBranch;
        this.totalAmount = totalAmount;
        this.currency = currency == null || currency.isBlank() ? "EUR" : currency;
        this.bonusBalance = bonusBalance;
        this.bonusPoints = bonusPoints;
        this.bonusType = bonusType;
        markManuallyEdited();
    }

    public void markManuallyEdited() {
        parseStatus = ParseStatus.MANUALLY_EDITED;
        parseErrorMessage = null;
    }

    public void markDeleted(DeleteReason reason) {
        deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        deleteReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Integer getPaperlessDocumentId() {
        return paperlessDocumentId;
    }

    public OffsetDateTime getImportedAt() {
        return importedAt;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public LocalTime getReceiptTime() {
        return receiptTime;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public DeleteReason getDeleteReason() {
        return deleteReason;
    }

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getRawText() {
        return rawText;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getStoreBranch() {
        return storeBranch;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBonusBalance() {
        return bonusBalance;
    }

    public BigDecimal getBonusPoints() {
        return bonusPoints;
    }

    public String getBonusType() {
        return bonusType;
    }

    public List<ReceiptItem> getItems() {
        return List.copyOf(items);
    }

    public String getParseErrorMessage() {
        return parseErrorMessage;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }
}
