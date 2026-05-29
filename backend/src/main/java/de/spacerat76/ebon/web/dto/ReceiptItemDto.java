package de.spacerat76.ebon.web.dto;

import java.math.BigDecimal;

public class ReceiptItemDto {
    private Long id;
    private Integer positionIndex;
    private String description;
    private BigDecimal totalPrice;
    private String category;

    public ReceiptItemDto() {
    }

    public ReceiptItemDto(Long id, Integer positionIndex, String description, BigDecimal totalPrice, String category) {
        this.id = id;
        this.positionIndex = positionIndex;
        this.description = description;
        this.totalPrice = totalPrice;
        this.category = category;
    }

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

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
