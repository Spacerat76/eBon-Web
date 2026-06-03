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
@Table(name = "receipt_item")
public class ReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "position_index", nullable = false)
    private int positionIndex;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(length = 32)
    private String unit;

    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", length = 32)
    private CategorySource categorySource;

    @Column(name = "is_manually_edited", nullable = false)
    private boolean manuallyEdited;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ReceiptItem() {
    }

    public ReceiptItem(int positionIndex, String description, BigDecimal totalPrice) {
        this.positionIndex = positionIndex;
        this.description = description;
        this.totalPrice = totalPrice;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void assignCategory(Category category, CategorySource source) {
        if (category == null && source != null) {
            throw new IllegalArgumentException("categorySource darf ohne Kategorie nicht gesetzt werden.");
        }
        if (category != null && source == null) {
            throw new IllegalArgumentException("categorySource muss mit Kategorie gesetzt werden.");
        }
        this.category = category;
        this.categorySource = source;
        if (source == CategorySource.MANUAL) {
            manuallyEdited = true;
        }
    }

    public void clearCategory() {
        this.category = null;
        this.categorySource = null;
    }

    public void manuallyClearCategory() {
        clearCategory();
        manuallyEdited = true;
    }

    public void updateManualValues(
            Integer positionIndex,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal discountAmount) {
        if (positionIndex != null) {
            this.positionIndex = positionIndex;
        }
        if (description != null) {
            this.description = description;
        }
        if (quantity != null) {
            this.quantity = quantity;
        }
        if (unit != null) {
            this.unit = unit;
        }
        if (unitPrice != null) {
            this.unitPrice = unitPrice;
        }
        if (totalPrice != null) {
            this.totalPrice = totalPrice;
        }
        if (discountAmount != null) {
            this.discountAmount = discountAmount;
        }
        manuallyEdited = true;
    }

    public void updateParsedValues(
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal discountAmount) {
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.discountAmount = discountAmount;
    }

    void setReceipt(Receipt receipt) {
        this.receipt = receipt;
    }

    public Long getId() {
        return id;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public int getPositionIndex() {
        return positionIndex;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public Category getCategory() {
        return category;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    public boolean isManuallyEdited() {
        return manuallyEdited;
    }
}
