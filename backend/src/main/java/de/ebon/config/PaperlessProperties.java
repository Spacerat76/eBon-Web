package de.ebon.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.paperless")
public class PaperlessProperties {

    @NotBlank
    private String baseUrl = "http://localhost:8000";

    @NotBlank
    private String apiToken = "change_me_paperless_token";

    @NotBlank
    private String ebonTag = "eBON";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getEbonTag() {
        return ebonTag;
    }

    public void setEbonTag(String ebonTag) {
        this.ebonTag = ebonTag;
    }
}
