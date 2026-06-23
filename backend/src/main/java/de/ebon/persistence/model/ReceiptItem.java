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
    @JoinColumn(name = "product_family_id")
    private ProductFamily productFamily;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_assignment_source", length = 32)
    private ProductAssignmentSource productAssignmentSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_assignment_status", length = 32)
    private ProductAssignmentStatus productAssignmentStatus;

    @Column(name = "product_assignment_confidence", precision = 4, scale = 3)
    private BigDecimal productAssignmentConfidence;

    @Column(name = "product_assignment_updated_at")
    private OffsetDateTime productAssignmentUpdatedAt;

    @Column(name = "exclude_from_product_price_comparison", nullable = false)
    private boolean excludedFromProductPriceComparison;

    @Column(name = "product_price_exclusion_reason", columnDefinition = "text")
    private String productPriceExclusionReason;

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

    public void assignProduct(
            ProductFamily family,
            ProductVariant variant,
            ProductAssignmentSource source,
            ProductAssignmentStatus status,
            BigDecimal confidence) {
        if (family == null || source == null || status == null) {
            throw new IllegalArgumentException("Produktzuordnung braucht Familie, Quelle und Status.");
        }
        if (variant != null && variant.getProductFamily() != family) {
            throw new IllegalArgumentException("Produktvariante gehoert nicht zur Produktfamilie.");
        }
        this.productFamily = family;
        this.productVariant = variant;
        this.productAssignmentSource = source;
        this.productAssignmentStatus = status;
        this.productAssignmentConfidence = confidence;
        this.productAssignmentUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markProductNeedsReview(BigDecimal confidence) {
        this.productFamily = null;
        this.productVariant = null;
        this.productAssignmentSource = null;
        this.productAssignmentStatus = ProductAssignmentStatus.NEEDS_REVIEW;
        this.productAssignmentConfidence = confidence;
        this.productAssignmentUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markNoProduct() {
        this.productFamily = null;
        this.productVariant = null;
        this.productAssignmentSource = null;
        this.productAssignmentStatus = ProductAssignmentStatus.NO_PRODUCT;
        this.productAssignmentConfidence = null;
        this.productAssignmentUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markProductRejected() {
        this.productFamily = null;
        this.productVariant = null;
        this.productAssignmentSource = null;
        this.productAssignmentStatus = ProductAssignmentStatus.REJECTED;
        this.productAssignmentConfidence = null;
        this.productAssignmentUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void clearProductAssignment() {
        this.productFamily = null;
        this.productVariant = null;
        this.productAssignmentSource = null;
        this.productAssignmentStatus = null;
        this.productAssignmentConfidence = null;
        this.productAssignmentUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void setProductPriceExclusion(boolean excluded, String reason) {
        this.excludedFromProductPriceComparison = excluded;
        this.productPriceExclusionReason = excluded && reason != null && !reason.isBlank() ? reason.trim() : null;
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

    public ProductFamily getProductFamily() {
        return productFamily;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public ProductAssignmentSource getProductAssignmentSource() {
        return productAssignmentSource;
    }

    public ProductAssignmentStatus getProductAssignmentStatus() {
        return productAssignmentStatus;
    }

    public BigDecimal getProductAssignmentConfidence() {
        return productAssignmentConfidence;
    }

    public OffsetDateTime getProductAssignmentUpdatedAt() {
        return productAssignmentUpdatedAt;
    }

    public boolean isExcludedFromProductPriceComparison() {
        return excludedFromProductPriceComparison;
    }

    public String getProductPriceExclusionReason() {
        return productPriceExclusionReason;
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
