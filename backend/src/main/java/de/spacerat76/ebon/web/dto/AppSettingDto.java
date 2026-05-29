package de.spacerat76.ebon.web.dto;

import java.time.OffsetDateTime;

public class AppSettingDto {
    private String key;
    private String value;
    private OffsetDateTime updatedAt;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
