package de.spacerat76.ebon.web.dto;

import java.math.BigDecimal;

public class ReceiptItemUpdateDto {
    private String description;
    private BigDecimal totalPrice;
    private Long categoryId;

    public ReceiptItemUpdateDto() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
