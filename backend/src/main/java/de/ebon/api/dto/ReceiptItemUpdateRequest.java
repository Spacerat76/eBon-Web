package de.ebon.api.dto;

import de.ebon.persistence.model.CategorySource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Teilupdate fuer eine Bon-Position. Explizites categoryId=null setzt die Position manuell auf Ohne Kategorie.")
public class ReceiptItemUpdateRequest {

    private Long id;
    private Integer positionIndex;

    @Size(min = 1, max = 512)
    private String description;

    @DecimalMin(value = "0.000", inclusive = false)
    @Digits(integer = 7, fraction = 3)
    private BigDecimal quantity;

    @Size(max = 32)
    private String unit;

    @Digits(integer = 8, fraction = 2)
    private BigDecimal unitPrice;

    @Digits(integer = 8, fraction = 2)
    private BigDecimal totalPrice;

    @Digits(integer = 8, fraction = 2)
    private BigDecimal discountAmount;

    @Schema(nullable = true, description = "Kategorie-ID oder null fuer Ohne Kategorie.")
    private Long categoryId;

    @Schema(nullable = true, allowableValues = {"RULE", "AI", "MANUAL"},
            description = "Bei Ohne Kategorie muss categorySource null sein. User-Updates werden als MANUAL gespeichert.")
    private CategorySource categorySource;

    private boolean categoryIdProvided;
    private boolean categorySourceProvided;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPositionIndex() {
        return positionIndex;
    }

    public void setPositionIndex(Integer positionIndex) {
        this.positionIndex = positionIndex;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        this.categoryIdProvided = true;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    public void setCategorySource(CategorySource categorySource) {
        this.categorySource = categorySource;
        this.categorySourceProvided = true;
    }

    @Schema(hidden = true)
    public boolean isCategoryIdProvided() {
        return categoryIdProvided;
    }

    @Schema(hidden = true)
    public boolean isCategorySourceProvided() {
        return categorySourceProvided;
    }

    @AssertTrue(message = "categorySource darf ohne categoryId nicht gesetzt sein.")
    @Schema(hidden = true)
    public boolean isCategoryConsistent() {
        if (!categoryIdProvided && !categorySourceProvided) {
            return true;
        }
        return categoryId != null || categorySource == null;
    }
}
