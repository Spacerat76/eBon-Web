package de.spacerat76.ebon.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AiParseResult {
    private String storeName;
    private BigDecimal totalAmount;
    private LocalDate receiptDate;

    // Additional structured fields
    private List<AiParseItem> items;
    private String currency;
    private BigDecimal bonusBalance;
    private BigDecimal bonusPoints;
    private java.math.BigDecimal cost;

    // Optional suggestion to persist as a ParseRule
    private String suggestedParseRegex;
    private String suggestedParseName;
    private Integer suggestedParsePriority;

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

    public List<AiParseItem> getItems() {
        return items;
    }

    public void setItems(List<AiParseItem> items) {
        this.items = items;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBonusBalance() {
        return bonusBalance;
    }

    public void setBonusBalance(BigDecimal bonusBalance) {
        this.bonusBalance = bonusBalance;
    }

    public BigDecimal getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(BigDecimal bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public java.math.BigDecimal getCost() {
        return cost;
    }

    public void setCost(java.math.BigDecimal cost) {
        this.cost = cost;
    }

    public String getSuggestedParseRegex() {
        return suggestedParseRegex;
    }

    public void setSuggestedParseRegex(String suggestedParseRegex) {
        this.suggestedParseRegex = suggestedParseRegex;
    }

    public String getSuggestedParseName() {
        return suggestedParseName;
    }

    public void setSuggestedParseName(String suggestedParseName) {
        this.suggestedParseName = suggestedParseName;
    }

    public Integer getSuggestedParsePriority() {
        return suggestedParsePriority;
    }

    public void setSuggestedParsePriority(Integer suggestedParsePriority) {
        this.suggestedParsePriority = suggestedParsePriority;
    }
}
