package de.ebon.api.service;

import de.ebon.api.dto.SettingsDto;
import de.ebon.api.dto.SettingsConnectionTestRequest;
import de.ebon.api.dto.SettingsConnectionTestResponse;
import de.ebon.config.AiCategorizationProperties;
import de.ebon.config.PaperlessProperties;
import de.ebon.persistence.model.AppSetting;
import de.ebon.persistence.repository.AppSettingRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceTests {

    private final AppSettingRepository appSettingRepository = mock(AppSettingRepository.class);
    private final PaperlessProperties paperlessProperties = new PaperlessProperties();
    private final AiCategorizationProperties aiProperties = new AiCategorizationProperties();
    private final Map<String, AppSetting> settings = new HashMap<>();
    private final SettingsService settingsService = new SettingsService(
            appSettingRepository,
            paperlessProperties,
            aiProperties);

    @BeforeEach
    void setUp() {
        settings.clear();
        paperlessProperties.setBaseUrl("http://paperless.local");
        paperlessProperties.setApiToken("paperless-secret");
        paperlessProperties.setEbonTag("eBON");
        aiProperties.setOpenrouterApiKey("openrouter-secret");
        aiProperties.setModel("google/gemini-flash-1.5");

        when(appSettingRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(settings.get(invocation.getArgument(0))));
        when(appSettingRepository.save(any(AppSetting.class))).thenAnswer(invocation -> {
            AppSetting setting = invocation.getArgument(0);
            settings.put(setting.getKey(), setting);
            return setting;
        });
    }

    // Verifies the settings API contract: secrets must be masked and invalid persisted values must not break defaults.
    @Test
    void getSettingsMasksSecretsAndFallsBackForInvalidStoredValues() {
        settings.put("paperless_api_token", new AppSetting("paperless_api_token", "stored-paperless-secret", "secret"));
        settings.put("openrouter_api_key", new AppSetting("openrouter_api_key", "stored-openrouter-secret", "secret"));
        settings.put("ai_categorization_min_confidence", new AppSetting("ai_categorization_min_confidence", "abc", "confidence"));
        settings.put("sync_interval_minutes", new AppSetting("sync_interval_minutes", "not-a-number", "sync"));

        SettingsDto dto = settingsService.getSettings();

        assertThat(dto.paperlessBaseUrl()).isEqualTo("http://paperless.local");
        assertThat(dto.paperlessApiToken()).isEqualTo("********");
        assertThat(dto.paperlessEbonTag()).isEqualTo("eBON");
        assertThat(dto.openRouterApiKey()).isEqualTo("********");
        assertThat(dto.openRouterModel()).isEqualTo("google/gemini-flash-1.5");
        assertThat(dto.aiCategorizationMinConfidence()).isEqualByComparingTo("0.900");
        assertThat(dto.syncIntervalMinutes()).isEqualTo(60);
        assertThat(dto.currency()).isEqualTo("EUR");
    }

    // Verifies that masked placeholders are never written back as real secrets while editable settings are persisted.
    @Test
    void updateIgnoresMaskedSecretsAndPersistsNewConfidenceAndInterval() {
        settings.put("paperless_api_token", new AppSetting("paperless_api_token", "stored-paperless-secret", "secret"));
        settings.put("openrouter_api_key", new AppSetting("openrouter_api_key", "stored-openrouter-secret", "secret"));
        settings.put("ai_categorization_min_confidence", new AppSetting("ai_categorization_min_confidence", "0.750", "confidence"));
        settings.put("sync_interval_minutes", new AppSetting("sync_interval_minutes", "30", "sync"));

        SettingsDto updated = settingsService.update(new SettingsDto(
                null,
                "********",
                null,
                "********",
                null,
                new BigDecimal("0.875"),
                15,
                null));

        assertThat(settings.get("paperless_api_token").getValue()).isEqualTo("stored-paperless-secret");
        assertThat(settings.get("openrouter_api_key").getValue()).isEqualTo("stored-openrouter-secret");
        assertThat(settings.get("ai_categorization_min_confidence").getValue()).isEqualTo("0.875");
        assertThat(settings.get("sync_interval_minutes").getValue()).isEqualTo("15");
        assertThat(updated.paperlessApiToken()).isEqualTo("********");
        assertThat(updated.openRouterApiKey()).isEqualTo("********");
        assertThat(updated.aiCategorizationMinConfidence()).isEqualByComparingTo("0.875");
        assertThat(updated.syncIntervalMinutes()).isEqualTo(15);

        ArgumentCaptor<AppSetting> settingCaptor = ArgumentCaptor.forClass(AppSetting.class);
        verify(appSettingRepository, times(2)).save(settingCaptor.capture());
        assertThat(settingCaptor.getAllValues())
                .extracting(AppSetting::getKey, AppSetting::getValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ai_categorization_min_confidence", "0.875"),
                        org.assertj.core.groups.Tuple.tuple("sync_interval_minutes", "15"));
    }

    // Verifies the current Phase 10 placeholder behavior for Paperless connection checks without doing a real call.
    @Test
    void testConnectionForPaperlessReflectsConfiguredUrlPresence() {
        SettingsConnectionTestResponse response = settingsService.testConnection(
                new SettingsConnectionTestRequest(SettingsConnectionTestRequest.Target.PAPERLESS));

        assertThat(response.target()).isEqualTo("PAPERLESS");
        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("Phase 10");
    }

    // Verifies the current Phase 10 placeholder behavior for OpenRouter connection checks without doing a real call.
    @Test
    void testConnectionForOpenRouterIsAlwaysPrepared() {
        SettingsConnectionTestResponse response = settingsService.testConnection(
                new SettingsConnectionTestRequest(SettingsConnectionTestRequest.Target.OPENROUTER));

        assertThat(response.target()).isEqualTo("OPENROUTER");
        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("OpenRouter");
    }
}
