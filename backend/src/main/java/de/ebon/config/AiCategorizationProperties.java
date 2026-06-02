package de.ebon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiCategorizationProperties {

    private String openrouterBaseUrl = "https://openrouter.ai/api/v1";
    private String openrouterApiKey = "";
    private String model = "google/gemini-flash-1.5";
    private int maxTokens = 500;
    private double temperature = 0.1;

    public boolean hasApiKey() {
        return openrouterApiKey != null && !openrouterApiKey.isBlank();
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
