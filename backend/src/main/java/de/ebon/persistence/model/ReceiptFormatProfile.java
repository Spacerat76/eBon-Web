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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "receipt_format_profile")
public class ReceiptFormatProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private FormatProfileScope scope;

    @Column(name = "store_name_key", nullable = false, length = 255, updatable = false)
    private String storeNameKey;

    @Column(name = "store_branch_key", nullable = false, length = 255, updatable = false)
    private String storeBranchKey;

    @Column(nullable = false, length = 128, updatable = false)
    private String fingerprint;

    @Column(name = "fingerprint_version", nullable = false, updatable = false)
    private int fingerprintVersion;

    @Column(name = "profile_schema_version", nullable = false, updatable = false)
    private int profileSchemaVersion = 1;

    @Column(nullable = false, updatable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predecessor_id", updatable = false)
    private ReceiptFormatProfile predecessor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_definition", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String profileDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FormatProfileState state = FormatProfileState.QUARANTINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private FormatProfileSource source;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "replaced_at")
    private OffsetDateTime replacedAt;

    @Column(name = "suspension_reason", columnDefinition = "text")
    private String suspensionReason;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "monitored_hit_count", nullable = false)
    private int monitoredHitCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ReceiptFormatProfile() {
    }

    public ReceiptFormatProfile(
            FormatProfileScope scope,
            String storeNameKey,
            String storeBranchKey,
            String fingerprint,
            int fingerprintVersion,
            int version,
            String profileDefinition,
            FormatProfileSource source,
            ReceiptFormatProfile predecessor) {
        this.scope = scope;
        this.storeNameKey = storeNameKey;
        this.storeBranchKey = storeBranchKey;
        this.fingerprint = fingerprint;
        this.fingerprintVersion = fingerprintVersion;
        this.version = version;
        this.profileDefinition = profileDefinition;
        this.source = source;
        this.predecessor = predecessor;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void activate() {
        state = FormatProfileState.ACTIVE;
        activatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        suspendedAt = null;
        suspensionReason = null;
    }

    public void suspend(String reason) {
        state = FormatProfileState.SUSPENDED;
        suspendedAt = OffsetDateTime.now(ZoneOffset.UTC);
        suspensionReason = reason;
    }

    public void retire() {
        state = FormatProfileState.RETIRED;
        replacedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public FormatProfileScope getScope() {
        return scope;
    }

    public String getStoreNameKey() {
        return storeNameKey;
    }

    public String getStoreBranchKey() {
        return storeBranchKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public int getFingerprintVersion() {
        return fingerprintVersion;
    }

    public int getProfileSchemaVersion() {
        return profileSchemaVersion;
    }

    public int getVersion() {
        return version;
    }

    public ReceiptFormatProfile getPredecessor() {
        return predecessor;
    }

    public String getProfileDefinition() {
        return profileDefinition;
    }

    public FormatProfileState getState() {
        return state;
    }

    public FormatProfileSource getSource() {
        return source;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public OffsetDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public OffsetDateTime getReplacedAt() {
        return replacedAt;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public int getHitCount() {
        return hitCount;
    }

    public int getMonitoredHitCount() {
        return monitoredHitCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
