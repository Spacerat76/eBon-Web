package de.spacerat76.ebon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ebon")
public class AppProperties {
    private String paperlessBaseUrl;
    private String paperlessApiToken;
    private String paperlessEbonTag = "eBON";
    private String openrouterBaseUrl;
    private String openrouterApiKey;
    private String openrouterModel;
    private String appApiToken;
    private int syncIntervalMinutes = 60;
    private int aiRateLimitPerMinute = 10;

    public String getPaperlessBaseUrl() {
        return paperlessBaseUrl;
    }

    public void setPaperlessBaseUrl(String paperlessBaseUrl) {
        this.paperlessBaseUrl = paperlessBaseUrl;
    }

    public String getPaperlessApiToken() {
        return paperlessApiToken;
    }

    public void setPaperlessApiToken(String paperlessApiToken) {
        this.paperlessApiToken = paperlessApiToken;
    }

    public String getPaperlessEbonTag() {
        return paperlessEbonTag;
    }

    public void setPaperlessEbonTag(String paperlessEbonTag) {
        this.paperlessEbonTag = paperlessEbonTag;
    }

    public String getOpenrouterBaseUrl() {
        return openrouterBaseUrl;
    }

    public void setOpenrouterBaseUrl(String openrouterBaseUrl) {
        this.openrouterBaseUrl = openrouterBaseUrl;
    }

    public String getOpenrouterApiKey() {
        return openrouterApiKey;
    }

    public void setOpenrouterApiKey(String openrouterApiKey) {
        this.openrouterApiKey = openrouterApiKey;
    }

    public String getOpenrouterModel() {
        return openrouterModel;
    }

    public void setOpenrouterModel(String openrouterModel) {
        this.openrouterModel = openrouterModel;
    }

    public String getAppApiToken() {
        return appApiToken;
    }

    public void setAppApiToken(String appApiToken) {
        this.appApiToken = appApiToken;
    }

    public int getSyncIntervalMinutes() {
        return syncIntervalMinutes;
    }

    public void setSyncIntervalMinutes(int syncIntervalMinutes) {
        this.syncIntervalMinutes = syncIntervalMinutes;
    }

    public int getAiRateLimitPerMinute() {
        return aiRateLimitPerMinute;
    }

    public void setAiRateLimitPerMinute(int aiRateLimitPerMinute) {
        this.aiRateLimitPerMinute = aiRateLimitPerMinute;
    }
}
