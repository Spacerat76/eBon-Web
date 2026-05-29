package de.spacerat76.ebon.web.dto;

import java.time.OffsetDateTime;

public class ParseRuleDto {
    private Long id;
    private String name;
    private String description;
    private String storeNamePattern;
    private String regex;
    private Integer priority;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStoreNamePattern() { return storeNamePattern; }
    public void setStoreNamePattern(String storeNamePattern) { this.storeNamePattern = storeNamePattern; }
    public String getRegex() { return regex; }
    public void setRegex(String regex) { this.regex = regex; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
