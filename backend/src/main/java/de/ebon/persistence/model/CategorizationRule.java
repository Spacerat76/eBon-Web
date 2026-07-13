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

@Entity
@Table(name = "categorization_rule")
public class CategorizationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_field", nullable = false, length = 32)
    private RuleMatchField matchField;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 32)
    private RuleMatchType matchType;

    @Column(name = "match_value", nullable = false, length = 512)
    private String matchValue;

    @Column(name = "store_name", length = 255)
    private String storeName;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CategorizationRule() {
    }

    public CategorizationRule(Category category, RuleMatchField matchField, RuleMatchType matchType, String matchValue, int priority) {
        this(category, matchField, matchType, matchValue, null, priority);
    }

    public CategorizationRule(
            Category category,
            RuleMatchField matchField,
            RuleMatchType matchType,
            String matchValue,
            String storeName,
            int priority) {
        this.category = category;
        this.matchField = matchField;
        this.matchType = matchType;
        this.matchValue = matchValue;
        this.storeName = normalizeStoreName(storeName);
        this.priority = priority;
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

    public Category getCategory() {
        return category;
    }

    public RuleMatchField getMatchField() {
        return matchField;
    }

    public RuleMatchType getMatchType() {
        return matchType;
    }

    public String getMatchValue() {
        return matchValue;
    }

    public String getStoreName() {
        return storeName;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void update(
            Category category,
            RuleMatchField matchField,
            RuleMatchType matchType,
            String matchValue,
            Integer priority,
            Boolean active) {
        update(category, matchField, matchType, matchValue, storeName, priority, active);
    }

    public void update(
            Category category,
            RuleMatchField matchField,
            RuleMatchType matchType,
            String matchValue,
            String storeName,
            Integer priority,
            Boolean active) {
        if (category != null) {
            this.category = category;
        }
        if (matchField != null) {
            this.matchField = matchField;
        }
        if (matchType != null) {
            this.matchType = matchType;
        }
        if (matchValue != null) {
            this.matchValue = matchValue;
        }
        this.storeName = normalizeStoreName(storeName);
        if (priority != null) {
            this.priority = priority;
        }
        if (active != null) {
            this.active = active;
        }
    }

    public void deactivate() {
        active = false;
    }

    private static String normalizeStoreName(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
