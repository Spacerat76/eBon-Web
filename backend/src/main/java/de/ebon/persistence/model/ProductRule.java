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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "product_rule")
public class ProductRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_family_id", nullable = false)
    private ProductFamily productFamily;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @Column(name = "store_name", length = 255)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 32)
    private RuleMatchType matchType;

    @Column(name = "match_value", nullable = false, length = 512)
    private String matchValue;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProductRule() {
    }

    public ProductRule(
            ProductFamily productFamily,
            ProductVariant productVariant,
            String storeName,
            RuleMatchType matchType,
            String matchValue,
            int priority) {
        update(productFamily, productVariant, storeName, matchType, matchValue, priority, true);
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void update(
            ProductFamily productFamily,
            ProductVariant productVariant,
            String storeName,
            RuleMatchType matchType,
            String matchValue,
            int priority,
            boolean active) {
        if (productFamily == null || matchType == null || matchValue == null || matchValue.isBlank()) {
            throw new IllegalArgumentException("Produktregel ist unvollstaendig.");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("Produktregel-Prioritaet darf nicht negativ sein.");
        }
        if (productVariant != null && productVariant.getProductFamily() != productFamily) {
            throw new IllegalArgumentException("Produktvariante gehoert nicht zur Produktfamilie.");
        }
        this.productFamily = productFamily;
        this.productVariant = productVariant;
        this.storeName = trim(storeName);
        this.matchType = matchType;
        this.matchValue = matchValue.trim();
        this.priority = priority;
        this.active = active;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() {
        return id;
    }

    public ProductFamily getProductFamily() {
        return productFamily;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public String getStoreName() {
        return storeName;
    }

    public RuleMatchType getMatchType() {
        return matchType;
    }

    public String getMatchValue() {
        return matchValue;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }
}
