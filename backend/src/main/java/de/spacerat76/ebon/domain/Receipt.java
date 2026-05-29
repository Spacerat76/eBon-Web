package de.spacerat76.ebon.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receipt")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paperless_document_id", nullable = false, unique = true)
    private Integer paperlessDocumentId;

    @Column(name = "imported_at")
    private OffsetDateTime importedAt;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Column(name = "receipt_time")
    private LocalTime receiptTime;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "store_branch")
    private String storeBranch;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "currency")
    private String currency;

    @Lob
    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(name = "bonus_balance")
    private BigDecimal bonusBalance;

    @Column(name = "bonus_points")
    private BigDecimal bonusPoints;

    @Column(name = "bonus_type")
    private String bonusType;

    @Column(name = "parse_status", nullable = false)
    private String parseStatus;

    @Lob
    @Column(name = "parse_error_message")
    private String parseErrorMessage;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptItem> items = new ArrayList<>();

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPaperlessDocumentId() {
        return paperlessDocumentId;
    }

    public void setPaperlessDocumentId(Integer paperlessDocumentId) {
        this.paperlessDocumentId = paperlessDocumentId;
    }

    public OffsetDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(OffsetDateTime importedAt) {
        this.importedAt = importedAt;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public void setReceiptDate(LocalDate receiptDate) {
        this.receiptDate = receiptDate;
    }

    public LocalTime getReceiptTime() {
        return receiptTime;
    }

    public void setReceiptTime(LocalTime receiptTime) {
        this.receiptTime = receiptTime;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getStoreBranch() {
        return storeBranch;
    }

    public void setStoreBranch(String storeBranch) {
        this.storeBranch = storeBranch;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public BigDecimal getBonusBalance() {
        return bonusBalance;
    }

    public void setBonusBalance(BigDecimal bonusBalance) {
        this.bonusBalance = bonusBalance;
    }

    public BigDecimal getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(BigDecimal bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public String getBonusType() {
        return bonusType;
    }

    public void setBonusType(String bonusType) {
        this.bonusType = bonusType;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getParseErrorMessage() {
        return parseErrorMessage;
    }

    public void setParseErrorMessage(String parseErrorMessage) {
        this.parseErrorMessage = parseErrorMessage;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ReceiptItem> getItems() {
        return items;
    }

    public void setItems(List<ReceiptItem> items) {
        this.items = items;
    }

    public void addItem(ReceiptItem item) {
        items.add(item);
        item.setReceipt(this);
    }

    public void removeItem(ReceiptItem item) {
        items.remove(item);
        item.setReceipt(null);
    }
}
