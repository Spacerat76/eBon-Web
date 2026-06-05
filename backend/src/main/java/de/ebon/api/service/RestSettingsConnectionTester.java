package de.ebon.api.service;

import de.ebon.api.dto.SettingsConnectionTestResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
class RestSettingsConnectionTester implements SettingsConnectionTester {

    private final RestClient.Builder restClientBuilder;

    RestSettingsConnectionTester(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public SettingsConnectionTestResponse testPaperless(String baseUrl, String apiToken) {
        if (isBlank(baseUrl) || isBlank(apiToken)) {
            return new SettingsConnectionTestResponse(
                    "PAPERLESS",
                    false,
                    "Paperless-URL oder API-Token fehlt.");
        }

        try {
            restClientBuilder.clone()
                    .baseUrl(baseUrl)
                    .build()
                    .get()
                    .uri("/api/documents/?page_size=1")
                    .header(HttpHeaders.AUTHORIZATION, "Token " + apiToken)
                    .retrieve()
                    .toBodilessEntity();
            return new SettingsConnectionTestResponse("PAPERLESS", true, "Paperless-NGX ist erreichbar.");
        } catch (RestClientException exception) {
            return new SettingsConnectionTestResponse(
                    "PAPERLESS",
                    false,
                    "Paperless-NGX konnte nicht erreicht werden.");
        }
    }

    @Override
    public SettingsConnectionTestResponse testOpenRouter(String baseUrl, String apiKey) {
        if (isBlank(baseUrl) || isBlank(apiKey)) {
            return new SettingsConnectionTestResponse(
                    "OPENROUTER",
                    false,
                    "OpenRouter-URL oder API-Key fehlt.");
        }

        try {
            restClientBuilder.clone()
                    .baseUrl(baseUrl)
                    .build()
                    .get()
                    .uri("/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .toBodilessEntity();
            return new SettingsConnectionTestResponse("OPENROUTER", true, "OpenRouter ist erreichbar.");
        } catch (RestClientException exception) {
            return new SettingsConnectionTestResponse(
                    "OPENROUTER",
                    false,
                    "OpenRouter konnte nicht erreicht werden.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
