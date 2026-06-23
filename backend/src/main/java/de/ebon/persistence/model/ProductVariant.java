package de.ebon.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "product_variant")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_family_id", nullable = false)
    private ProductFamily productFamily;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "unit_quantity", precision = 12, scale = 3)
    private BigDecimal unitQuantity;

    @Column(length = 32)
    private String unit;

    @Column(name = "package_quantity")
    private Integer packageQuantity;

    @Column(name = "package_description", length = 255)
    private String packageDescription;

    @Column(name = "total_quantity", precision = 12, scale = 3)
    private BigDecimal totalQuantity;

    @Column(name = "total_unit", length = 32)
    private String totalUnit;

    @Column(length = 32, unique = true)
    private String gtin;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProductVariant() {
    }

    public ProductVariant(
            ProductFamily productFamily,
            String name,
            BigDecimal unitQuantity,
            String unit,
            Integer packageQuantity,
            String packageDescription,
            BigDecimal totalQuantity,
            String totalUnit,
            String gtin) {
        this.productFamily = requireFamily(productFamily);
        updateValues(name, unitQuantity, unit, packageQuantity, packageDescription, totalQuantity, totalUnit, gtin, true);
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

    public void updateValues(
            String name,
            BigDecimal unitQuantity,
            String unit,
            Integer packageQuantity,
            String packageDescription,
            BigDecimal totalQuantity,
            String totalUnit,
            String gtin,
            boolean active) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Produktvariantenname darf nicht leer sein.");
        }
        if (packageQuantity != null && packageQuantity < 1) {
            throw new IllegalArgumentException("Packungsmenge muss mindestens eins sein.");
        }
        this.name = name.trim();
        this.unitQuantity = unitQuantity;
        this.unit = trim(unit);
        this.packageQuantity = packageQuantity;
        this.packageDescription = trim(packageDescription);
        this.totalQuantity = totalQuantity == null ? deriveTotalQuantity(unitQuantity, packageQuantity) : totalQuantity;
        this.totalUnit = trim(totalUnit == null ? unit : totalUnit);
        this.gtin = trim(gtin);
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    private ProductFamily requireFamily(ProductFamily family) {
        if (family == null) {
            throw new IllegalArgumentException("Produktvariante braucht eine Produktfamilie.");
        }
        return family;
    }

    private BigDecimal deriveTotalQuantity(BigDecimal quantity, Integer packages) {
        if (quantity == null || packages == null) {
            return quantity;
        }
        return quantity.multiply(BigDecimal.valueOf(packages));
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

    public String getName() {
        return name;
    }

    public BigDecimal getUnitQuantity() {
        return unitQuantity;
    }

    public String getUnit() {
        return unit;
    }

    public Integer getPackageQuantity() {
        return packageQuantity;
    }

    public String getPackageDescription() {
        return packageDescription;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public String getTotalUnit() {
        return totalUnit;
    }

    public String getGtin() {
        return gtin;
    }

    public boolean isActive() {
        return active;
    }
}
