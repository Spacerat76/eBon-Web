package de.ebon.api.service;

import de.ebon.api.dto.SettingsConnectionTestRequest;
import de.ebon.api.dto.SettingsConnectionTestResponse;
import de.ebon.api.dto.SettingsDto;
import de.ebon.config.AiCategorizationProperties;
import de.ebon.config.PaperlessProperties;
import de.ebon.persistence.model.AppSetting;
import de.ebon.persistence.repository.AppSettingRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final String MASK = "********";
    private static final BigDecimal DEFAULT_AI_CONFIDENCE = new BigDecimal("0.900");

    private final AppSettingRepository appSettingRepository;
    private final PaperlessProperties paperlessProperties;
    private final AiCategorizationProperties aiProperties;

    public SettingsService(
            AppSettingRepository appSettingRepository,
            PaperlessProperties paperlessProperties,
            AiCategorizationProperties aiProperties) {
        this.appSettingRepository = appSettingRepository;
        this.paperlessProperties = paperlessProperties;
        this.aiProperties = aiProperties;
    }

    @Transactional(readOnly = true)
    public SettingsDto getSettings() {
        String paperlessToken = value("paperless_api_token", paperlessProperties.getApiToken());
        String openRouterKey = value("openrouter_api_key", aiProperties.getOpenrouterApiKey());
        return new SettingsDto(
                value("paperless_base_url", paperlessProperties.getBaseUrl()),
                mask(paperlessToken),
                value("paperless_ebon_tag", paperlessProperties.getEbonTag()),
                mask(openRouterKey),
                value("openrouter_model", value("ai_model", aiProperties.getModel())),
                confidence(),
                integerValue("sync_interval_minutes", 60),
                value("currency", "EUR"));
    }

    @Transactional
    public SettingsDto update(SettingsDto request) {
        saveIfPresent("paperless_base_url", request.paperlessBaseUrl(), "Paperless-NGX Basis-URL");
        saveSecretIfPresent("paperless_api_token", request.paperlessApiToken(), "Paperless-NGX API-Token");
        saveIfPresent("paperless_ebon_tag", request.paperlessEbonTag(), "Paperless-NGX eBon-Tag");
        saveSecretIfPresent("openrouter_api_key", request.openRouterApiKey(), "OpenRouter API-Key");
        saveIfPresent("openrouter_model", request.openRouterModel(), "OpenRouter Modell");
        if (request.aiCategorizationMinConfidence() != null) {
            save("ai_categorization_min_confidence",
                    request.aiCategorizationMinConfidence().toPlainString(),
                    "Minimale KI-Konfidenz fuer automatische Kategorisierung");
        }
        if (request.syncIntervalMinutes() != null) {
            save("sync_interval_minutes", request.syncIntervalMinutes().toString(), "Sync-Intervall in Minuten");
        }
        saveIfPresent("currency", request.currency(), "Anzuzeigende Waehrung");
        return getSettings();
    }

    public SettingsConnectionTestResponse testConnection(SettingsConnectionTestRequest request) {
        return switch (request.target()) {
            case PAPERLESS -> new SettingsConnectionTestResponse(
                    "PAPERLESS",
                    !getSettings().paperlessBaseUrl().isBlank(),
                    "Paperless-Konfiguration ist vorhanden. Ein echter Verbindungstest folgt in Phase 10.");
            case OPENROUTER -> new SettingsConnectionTestResponse(
                    "OPENROUTER",
                    true,
                    "OpenRouter-Konfiguration ist gespeichert. Externe Test-Calls werden in Tests nicht ausgefuehrt.");
        };
    }

    private String value(String key, String fallback) {
        return appSettingRepository.findById(key)
                .map(AppSetting::getValue)
                .orElse(fallback);
    }

    private int integerValue(String key, int fallback) {
        try {
            return Integer.parseInt(value(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private BigDecimal confidence() {
        try {
            BigDecimal value = new BigDecimal(value("ai_categorization_min_confidence", DEFAULT_AI_CONFIDENCE.toPlainString()));
            return value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0
                    ? DEFAULT_AI_CONFIDENCE
                    : value;
        } catch (RuntimeException exception) {
            return DEFAULT_AI_CONFIDENCE;
        }
    }

    private void saveIfPresent(String key, String value, String description) {
        if (value != null) {
            save(key, value, description);
        }
    }

    private void saveSecretIfPresent(String key, String value, String description) {
        if (value == null || MASK.equals(value)) {
            return;
        }
        save(key, value, description);
    }

    private void save(String key, String value, String description) {
        AppSetting setting = appSettingRepository.findById(key)
                .orElseGet(() -> new AppSetting(key, value, description));
        setting.updateValue(value);
        appSettingRepository.save(setting);
    }

    private String mask(String value) {
        return value == null || value.isBlank() ? null : MASK;
    }
}
