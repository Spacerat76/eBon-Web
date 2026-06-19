package de.ebon.api.service;

import de.ebon.api.dto.SettingsConnectionTestRequest;
import de.ebon.api.dto.SettingsConnectionTestResponse;
import de.ebon.api.dto.SettingsDto;
import de.ebon.config.AiCategorizationProperties;
import de.ebon.config.AiParsingProperties;
import de.ebon.config.PaperlessProperties;
import de.ebon.parser.AiParsingTextMode;
import de.ebon.persistence.model.AppSetting;
import de.ebon.persistence.repository.AppSettingRepository;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final String MASK = "********";
    private static final String LEGACY_UNAVAILABLE_MODEL = "google/gemini-flash-1.5";
    private static final BigDecimal DEFAULT_AI_CONFIDENCE = new BigDecimal("0.900");
    private static final BigDecimal DEFAULT_AI_PARSING_CONFIDENCE = new BigDecimal("0.900");

    private final AppSettingRepository appSettingRepository;
    private final PaperlessProperties paperlessProperties;
    private final AiCategorizationProperties aiProperties;
    private final AiParsingProperties aiParsingProperties;
    private final SettingsConnectionTester connectionTester;

    public SettingsService(
            AppSettingRepository appSettingRepository,
            PaperlessProperties paperlessProperties,
            AiCategorizationProperties aiProperties,
            SettingsConnectionTester connectionTester) {
        this(
                appSettingRepository,
                paperlessProperties,
                aiProperties,
                new AiParsingProperties(),
                connectionTester);
    }

    @Autowired
    public SettingsService(
            AppSettingRepository appSettingRepository,
            PaperlessProperties paperlessProperties,
            AiCategorizationProperties aiProperties,
            AiParsingProperties aiParsingProperties,
            SettingsConnectionTester connectionTester) {
        this.appSettingRepository = appSettingRepository;
        this.paperlessProperties = paperlessProperties;
        this.aiProperties = aiProperties;
        this.aiParsingProperties = aiParsingProperties;
        this.connectionTester = connectionTester;
    }

    @Transactional(readOnly = true)
    public SettingsDto getSettings() {
        String paperlessToken = value("paperless_api_token", paperlessProperties.getApiToken());
        String openRouterKey = value("openrouter_api_key", aiProperties.getOpenrouterApiKey());
        String openRouterModel = openRouterModel();
        return new SettingsDto(
                value("paperless_base_url", paperlessProperties.getBaseUrl()),
                value("paperless_public_base_url", defaultPublicBaseUrl()),
                value("paperless_document_url_template", paperlessProperties.getDocumentUrlTemplate()),
                mask(paperlessToken),
                value("paperless_ebon_tag", paperlessProperties.getEbonTag()),
                mask(openRouterKey),
                value("openrouter_base_url", aiProperties.getOpenrouterBaseUrl()),
                openRouterModel,
                confidence(),
                booleanValue("ai_parsing_fallback_enabled", aiParsingProperties.isFallbackEnabled()),
                aiParsingModel(openRouterModel),
                integerValue("ai_parsing_max_tokens", aiParsingProperties.getMaxTokens()),
                doubleValue("ai_parsing_temperature", aiParsingProperties.getTemperature()),
                decimalValue("ai_parsing_min_confidence", DEFAULT_AI_PARSING_CONFIDENCE),
                integerValue("ai_parsing_sync_call_limit", aiParsingProperties.getSyncCallLimit()),
                enumValue("ai_parsing_text_mode", aiParsingProperties.getTextMode().name(), AiParsingTextMode.class),
                booleanValue("ai_parsing_store_debug_snippets", aiParsingProperties.isStoreDebugSnippets()),
                integerValue("sync_interval_minutes", 60),
                value("currency", "EUR"));
    }

    @Transactional
    public SettingsDto update(SettingsDto request) {
        saveIfPresent("paperless_base_url", request.paperlessBaseUrl(), "Paperless-NGX Basis-URL");
        saveIfPresent("paperless_public_base_url", request.paperlessPublicBaseUrl(), "Browser-erreichbare Paperless-Web-URL");
        saveIfPresent("paperless_document_url_template", request.paperlessDocumentUrlTemplate(), "Paperless-Dokument-URL-Vorlage");
        saveSecretIfPresent("paperless_api_token", request.paperlessApiToken(), "Paperless-NGX API-Token");
        saveIfPresent("paperless_ebon_tag", request.paperlessEbonTag(), "Paperless-NGX eBon-Tag");
        saveSecretIfPresent("openrouter_api_key", request.openRouterApiKey(), "OpenRouter API-Key");
        saveIfPresent("openrouter_base_url", request.openRouterBaseUrl(), "OpenRouter Basis-URL");
        if (request.openRouterModel() != null) {
            boolean aiParsingInheritsOpenRouterModel = aiParsingModelInheritsOpenRouterModel();
            save("openrouter_model", request.openRouterModel(), "OpenRouter Modell");
            if (aiParsingInheritsOpenRouterModel) {
                save("ai_parsing_model", request.openRouterModel(), "OpenRouter Modell fuer KI-Parsing");
            }
        }
        if (request.aiCategorizationMinConfidence() != null) {
            save("ai_categorization_min_confidence",
                    request.aiCategorizationMinConfidence().toPlainString(),
                    "Minimale KI-Konfidenz fuer automatische Kategorisierung");
        }
        if (request.aiParsingFallbackEnabled() != null) {
            save("ai_parsing_fallback_enabled",
                    request.aiParsingFallbackEnabled().toString(),
                    "OpenRouter KI-Parsing-Fallback aktivieren");
        }
        saveIfPresent("ai_parsing_model", request.aiParsingModel(), "OpenRouter Modell fuer KI-Parsing");
        if (request.aiParsingMaxTokens() != null) {
            save("ai_parsing_max_tokens", request.aiParsingMaxTokens().toString(), "Max Tokens fuer KI-Parsing");
        }
        if (request.aiParsingTemperature() != null) {
            save("ai_parsing_temperature", request.aiParsingTemperature().toString(), "Temperature fuer KI-Parsing");
        }
        if (request.aiParsingMinConfidence() != null) {
            save("ai_parsing_min_confidence",
                    request.aiParsingMinConfidence().toPlainString(),
                    "Minimale KI-Konfidenz fuer automatische Parser-Uebernahme");
        }
        if (request.aiParsingSyncCallLimit() != null) {
            save("ai_parsing_sync_call_limit",
                    request.aiParsingSyncCallLimit().toString(),
                    "Maximale KI-Parsing-Calls pro Sync-Lauf");
        }
        saveIfPresent("ai_parsing_text_mode", request.aiParsingTextMode(), "Textmodus fuer KI-Parsing");
        if (request.aiParsingStoreDebugSnippets() != null) {
            save("ai_parsing_store_debug_snippets",
                    request.aiParsingStoreDebugSnippets().toString(),
                    "Nur lokale Entwicklung: gekuerzte und maskierte KI-Prompt-/Antwort-Snippets speichern");
        }
        if (request.syncIntervalMinutes() != null) {
            save("sync_interval_minutes", request.syncIntervalMinutes().toString(), "Sync-Intervall in Minuten");
        }
        saveIfPresent("currency", request.currency(), "Anzuzeigende Waehrung");
        return getSettings();
    }

    public SettingsConnectionTestResponse testConnection(SettingsConnectionTestRequest request) {
        return switch (request.target()) {
            case PAPERLESS -> connectionTester.testPaperless(
                    value("paperless_base_url", paperlessProperties.getBaseUrl()),
                    value("paperless_api_token", paperlessProperties.getApiToken()));
            case OPENROUTER -> connectionTester.testOpenRouter(
                    value("openrouter_base_url", aiProperties.getOpenrouterBaseUrl()),
                    value("openrouter_api_key", aiProperties.getOpenrouterApiKey()));
        };
    }

    private String value(String key, String fallback) {
        return appSettingRepository.findById(key)
                .map(AppSetting::getValue)
                .orElse(fallback);
    }

    private String defaultPublicBaseUrl() {
        String publicBaseUrl = paperlessProperties.getPublicBaseUrl();
        return publicBaseUrl == null || publicBaseUrl.isBlank()
                ? paperlessProperties.getBaseUrl()
                : publicBaseUrl;
    }

    private String openRouterModel() {
        return value("openrouter_model", value("ai_model", aiProperties.getModel()));
    }

    private String aiParsingModel(String openRouterModel) {
        String configured = value("ai_parsing_model", null);
        if (configured == null || configured.isBlank() || LEGACY_UNAVAILABLE_MODEL.equals(configured)) {
            return openRouterModel == null || openRouterModel.isBlank()
                    ? aiParsingProperties.getModel()
                    : openRouterModel;
        }
        return configured;
    }

    private boolean aiParsingModelInheritsOpenRouterModel() {
        String configured = value("ai_parsing_model", null);
        return configured == null || configured.isBlank() || LEGACY_UNAVAILABLE_MODEL.equals(configured);
    }

    private int integerValue(String key, int fallback) {
        try {
            return Integer.parseInt(value(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double doubleValue(String key, double fallback) {
        try {
            return Double.parseDouble(value(key, Double.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean booleanValue(String key, boolean fallback) {
        String value = value(key, Boolean.toString(fallback));
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private BigDecimal confidence() {
        return decimalValue("ai_categorization_min_confidence", DEFAULT_AI_CONFIDENCE);
    }

    private BigDecimal decimalValue(String key, BigDecimal fallback) {
        try {
            BigDecimal value = new BigDecimal(value(key, fallback.toPlainString()));
            return value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0
                    ? fallback
                    : value;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private <E extends Enum<E>> String enumValue(String key, String fallback, Class<E> enumType) {
        String value = value(key, fallback);
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT)).name();
        } catch (RuntimeException exception) {
            return fallback;
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
