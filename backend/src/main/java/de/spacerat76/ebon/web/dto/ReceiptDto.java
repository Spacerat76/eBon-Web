package de.spacerat76.ebon.web.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReceiptDto {
    private Long id;
    private Integer paperlessDocumentId;
    private String storeName;
    private BigDecimal totalAmount;
    private String currency;
    private String parseStatus;
    private List<ReceiptItemDto> items = new ArrayList<>();

    public ReceiptDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPaperlessDocumentId() {
        return paperlessDocumentId;
    }

    public void setPaperlessDocumentId(Integer paperlessDocumentId) {
        this.paperlessDocumentId = paperlessDocumentId;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public List<ReceiptItemDto> getItems() {
        return items;
    }

    public void setItems(List<ReceiptItemDto> items) {
        this.items = items;
    }
}
