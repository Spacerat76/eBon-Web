package de.ebon.parser;

import de.ebon.config.AiCategorizationProperties;
import de.ebon.config.AiParsingProperties;
import de.ebon.persistence.model.AppSetting;
import de.ebon.persistence.repository.AppSettingRepository;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AiParsingSettingsService {

    private static final BigDecimal DEFAULT_MIN_CONFIDENCE = new BigDecimal("0.900");

    private final AppSettingRepository appSettingRepository;
    private final AiCategorizationProperties aiProperties;
    private final AiParsingProperties aiParsingProperties;

    public AiParsingSettingsService(
            AppSettingRepository appSettingRepository,
            AiCategorizationProperties aiProperties,
            AiParsingProperties aiParsingProperties) {
        this.appSettingRepository = appSettingRepository;
        this.aiProperties = aiProperties;
        this.aiParsingProperties = aiParsingProperties;
    }

    public AiParsingSettings current() {
        return new AiParsingSettings(
                booleanValue("ai_parsing_fallback_enabled", aiParsingProperties.isFallbackEnabled()),
                value("openrouter_base_url", aiProperties.getOpenrouterBaseUrl()),
                value("openrouter_api_key", aiProperties.getOpenrouterApiKey()),
                value("ai_parsing_model", aiParsingProperties.getModel()),
                integerValue("ai_parsing_max_tokens", aiParsingProperties.getMaxTokens()),
                doubleValue("ai_parsing_temperature", aiParsingProperties.getTemperature()),
                decimalValue("ai_parsing_min_confidence", DEFAULT_MIN_CONFIDENCE),
                integerValue("ai_parsing_sync_call_limit", aiParsingProperties.getSyncCallLimit()),
                enumValue("ai_parsing_text_mode", aiParsingProperties.getTextMode(), AiParsingTextMode.class),
                booleanValue("ai_parsing_store_debug_snippets", aiParsingProperties.isStoreDebugSnippets()));
    }

    private String value(String key, String fallback) {
        return appSettingRepository.findById(key)
                .map(AppSetting::getValue)
                .orElse(fallback);
    }

    private boolean booleanValue(String key, boolean fallback) {
        String value = value(key, Boolean.toString(fallback));
        return "true".equalsIgnoreCase(value) || "1".equals(value);
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

    private <E extends Enum<E>> E enumValue(String key, E fallback, Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, value(key, fallback.name()).toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
