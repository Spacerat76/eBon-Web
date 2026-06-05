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
    private final SettingsConnectionTester connectionTester = mock(SettingsConnectionTester.class);
    private final PaperlessProperties paperlessProperties = new PaperlessProperties();
    private final AiCategorizationProperties aiProperties = new AiCategorizationProperties();
    private final Map<String, AppSetting> settings = new HashMap<>();
    private final SettingsService settingsService = new SettingsService(
            appSettingRepository,
            paperlessProperties,
            aiProperties,
            connectionTester);

    @BeforeEach
    void setUp() {
        settings.clear();
        paperlessProperties.setBaseUrl("http://paperless.local");
        paperlessProperties.setPublicBaseUrl("http://paperless.public");
        paperlessProperties.setDocumentUrlTemplate("");
        paperlessProperties.setApiToken("paperless-secret");
        paperlessProperties.setEbonTag("eBON");
        aiProperties.setOpenrouterApiKey("openrouter-secret");
        aiProperties.setOpenrouterBaseUrl("https://openrouter.local/api/v1");
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
        assertThat(dto.paperlessPublicBaseUrl()).isEqualTo("http://paperless.public");
        assertThat(dto.paperlessDocumentUrlTemplate()).isEmpty();
        assertThat(dto.paperlessApiToken()).isEqualTo("********");
        assertThat(dto.paperlessEbonTag()).isEqualTo("eBON");
        assertThat(dto.openRouterApiKey()).isEqualTo("********");
        assertThat(dto.openRouterBaseUrl()).isEqualTo("https://openrouter.local/api/v1");
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
                "http://paperless.browser",
                "http://paperless.browser/documents/{paperlessDocumentId}/details",
                "********",
                null,
                "********",
                "https://openrouter.test/api/v1",
                null,
                new BigDecimal("0.875"),
                15,
                null));

        assertThat(settings.get("paperless_api_token").getValue()).isEqualTo("stored-paperless-secret");
        assertThat(settings.get("openrouter_api_key").getValue()).isEqualTo("stored-openrouter-secret");
        assertThat(settings.get("ai_categorization_min_confidence").getValue()).isEqualTo("0.875");
        assertThat(settings.get("sync_interval_minutes").getValue()).isEqualTo("15");
        assertThat(settings.get("openrouter_base_url").getValue()).isEqualTo("https://openrouter.test/api/v1");
        assertThat(settings.get("paperless_public_base_url").getValue()).isEqualTo("http://paperless.browser");
        assertThat(settings.get("paperless_document_url_template").getValue())
                .isEqualTo("http://paperless.browser/documents/{paperlessDocumentId}/details");
        assertThat(updated.paperlessApiToken()).isEqualTo("********");
        assertThat(updated.openRouterApiKey()).isEqualTo("********");
        assertThat(updated.aiCategorizationMinConfidence()).isEqualByComparingTo("0.875");
        assertThat(updated.syncIntervalMinutes()).isEqualTo(15);

        ArgumentCaptor<AppSetting> settingCaptor = ArgumentCaptor.forClass(AppSetting.class);
        verify(appSettingRepository, times(5)).save(settingCaptor.capture());
        assertThat(settingCaptor.getAllValues())
                .extracting(AppSetting::getKey, AppSetting::getValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("paperless_public_base_url", "http://paperless.browser"),
                        org.assertj.core.groups.Tuple.tuple("paperless_document_url_template", "http://paperless.browser/documents/{paperlessDocumentId}/details"),
                        org.assertj.core.groups.Tuple.tuple("openrouter_base_url", "https://openrouter.test/api/v1"),
                        org.assertj.core.groups.Tuple.tuple("ai_categorization_min_confidence", "0.875"),
                        org.assertj.core.groups.Tuple.tuple("sync_interval_minutes", "15"));
    }

    // Verifies Paperless connection checks are delegated to the mockable tester with unmasked settings.
    @Test
    void testConnectionForPaperlessReflectsConfiguredUrlPresence() {
        when(connectionTester.testPaperless("http://paperless.local", "paperless-secret"))
                .thenReturn(new SettingsConnectionTestResponse("PAPERLESS", true, "Paperless-NGX ist erreichbar."));

        SettingsConnectionTestResponse response = settingsService.testConnection(
                new SettingsConnectionTestRequest(SettingsConnectionTestRequest.Target.PAPERLESS));

        assertThat(response.target()).isEqualTo("PAPERLESS");
        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("erreichbar");
    }

    // Verifies OpenRouter connection checks are delegated to the mockable tester with unmasked settings.
    @Test
    void testConnectionForOpenRouterIsAlwaysPrepared() {
        when(connectionTester.testOpenRouter("https://openrouter.local/api/v1", "openrouter-secret"))
                .thenReturn(new SettingsConnectionTestResponse("OPENROUTER", true, "OpenRouter ist erreichbar."));

        SettingsConnectionTestResponse response = settingsService.testConnection(
                new SettingsConnectionTestRequest(SettingsConnectionTestRequest.Target.OPENROUTER));

        assertThat(response.target()).isEqualTo("OPENROUTER");
        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("erreichbar");
    }
}
