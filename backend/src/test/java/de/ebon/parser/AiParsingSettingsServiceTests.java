package de.ebon.parser;

import de.ebon.config.AiCategorizationProperties;
import de.ebon.config.AiParsingProperties;
import de.ebon.persistence.model.AppSetting;
import de.ebon.persistence.repository.AppSettingRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiParsingSettingsServiceTests {

    private final AppSettingRepository appSettingRepository = mock(AppSettingRepository.class);
    private final AiCategorizationProperties aiProperties = new AiCategorizationProperties();
    private final AiParsingProperties aiParsingProperties = new AiParsingProperties();
    private final Map<String, AppSetting> settings = new HashMap<>();
    private final AiParsingSettingsService service = new AiParsingSettingsService(
            appSettingRepository,
            aiProperties,
            aiParsingProperties);

    @BeforeEach
    void setUp() {
        settings.clear();
        aiProperties.setOpenrouterBaseUrl("https://openrouter.test/api/v1");
        aiProperties.setOpenrouterApiKey("secret");
        aiProperties.setModel("openai/gpt-oss-20b");
        aiParsingProperties.setModel("openai/gpt-oss-20b");
        when(appSettingRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(settings.get(invocation.getArgument(0))));
    }

    // Verifies the historical invalid seed no longer overrides the user-visible OpenRouter model setting.
    @Test
    void legacyAiParsingModelInheritsOpenRouterModel() {
        settings.put("openrouter_model", new AppSetting("openrouter_model", "openai/gpt-oss-20b", "model"));
        settings.put("ai_parsing_model", new AppSetting("ai_parsing_model", "google/gemini-flash-1.5", "legacy"));

        AiParsingSettings result = service.current();

        assertThat(result.model()).isEqualTo("openai/gpt-oss-20b");
    }

    // Verifies an explicit parsing model remains a real override when it differs from the legacy seed.
    @Test
    void explicitAiParsingModelOverridesOpenRouterModel() {
        settings.put("openrouter_model", new AppSetting("openrouter_model", "openai/gpt-oss-20b", "model"));
        settings.put("ai_parsing_model", new AppSetting("ai_parsing_model", "anthropic/claude-sonnet-4", "override"));

        AiParsingSettings result = service.current();

        assertThat(result.model()).isEqualTo("anthropic/claude-sonnet-4");
    }
}
