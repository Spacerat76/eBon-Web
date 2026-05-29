package de.spacerat76.ebon.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AiCategorizationLogDto {
    private Long id;
    private Long receiptId;
    private String requestPayload;
    private String responsePayload;
    private String model;
    private BigDecimal cost;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
