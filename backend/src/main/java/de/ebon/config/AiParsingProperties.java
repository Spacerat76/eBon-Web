package de.ebon.config;

import de.ebon.parser.AiParsingTextMode;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.parsing")
public class AiParsingProperties {

    private boolean fallbackEnabled = true;
    private String model = "google/gemini-flash-1.5";
    private int maxTokens = 2500;
    private double temperature = 0.0;
    private BigDecimal minConfidence = new BigDecimal("0.900");
    private int syncCallLimit = 25;
    private AiParsingTextMode textMode = AiParsingTextMode.MINIMIZED;
    private boolean storeDebugSnippets = false;

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
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

    public BigDecimal getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(BigDecimal minConfidence) {
        this.minConfidence = minConfidence;
    }

    public int getSyncCallLimit() {
        return syncCallLimit;
    }

    public void setSyncCallLimit(int syncCallLimit) {
        this.syncCallLimit = syncCallLimit;
    }

    public AiParsingTextMode getTextMode() {
        return textMode;
    }

    public void setTextMode(AiParsingTextMode textMode) {
        this.textMode = textMode;
    }

    public boolean isStoreDebugSnippets() {
        return storeDebugSnippets;
    }

    public void setStoreDebugSnippets(boolean storeDebugSnippets) {
        this.storeDebugSnippets = storeDebugSnippets;
    }
}
