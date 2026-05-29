package de.spacerat76.ebon.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AiParseResult {
    private String storeName;
    private BigDecimal totalAmount;
    private LocalDate receiptDate;

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

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public void setReceiptDate(LocalDate receiptDate) {
        this.receiptDate = receiptDate;
    }
}
