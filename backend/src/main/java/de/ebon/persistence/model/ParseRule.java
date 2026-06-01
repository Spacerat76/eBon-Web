package de.ebon.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "parse_rule")
public class ParseRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleSource source;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ParseRule() {
    }

    public ParseRule(String storeName, ParseRuleType ruleType, String matchRegex, String extractGroup, RuleSource source) {
        this.storeName = storeName;
        this.ruleType = ruleType;
        this.matchRegex = matchRegex;
        this.extractGroup = extractGroup;
        this.source = source;
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
}
