package de.ebon.parser;

import de.ebon.persistence.model.AiParsingLog;
import de.ebon.persistence.model.AiParsingStatus;
import de.ebon.persistence.model.AiParsingTrigger;
import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.repository.AiParsingLogRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiParsingFallbackServiceTests {

    private final AiParsingSettingsService settingsService = mock(AiParsingSettingsService.class);
    private final AiReceiptParsingClient aiClient = mock(AiReceiptParsingClient.class);
    private final AiParsingLogRepository logRepository = mock(AiParsingLogRepository.class);
    private final ParseRuleSuggestionWriter suggestionWriter = mock(ParseRuleSuggestionWriter.class);
    private final AiParsingFallbackService service = new AiParsingFallbackService(
            settingsService,
            aiClient,
            new AiReceiptJsonParser(new ObjectMapper()),
            logRepository,
            suggestionWriter,
            new ObjectMapper());

    AiParsingFallbackServiceTests() {
        when(logRepository.saveAndFlush(any(AiParsingLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void failedFallbackRetainsSelectedProfileEvidenceAndOriginalQualityFailure() {
        when(settingsService.current()).thenReturn(settings("secret"));
        var profile = new de.ebon.parser.profile.AppliedProfile(42L, 3,
                de.ebon.persistence.model.FormatProfileScope.STORE, "fingerprint");
        var trace = new de.ebon.parser.profile.ParsedLineTrace(1, 0, 3,
                de.ebon.persistence.model.ParseLineType.UNRESOLVED, null, java.util.Map.of(), "UNKNOWN");
        ReceiptParseResult input = new ReceiptParseResult(ParseStatus.PARSE_REVIEW, null, "UNRESOLVED_LINES",
                ParseSource.RULE, profile, java.util.List.of(trace));
        ReceiptParseResult result = service.tryFallback(receipt(), input, options(AiParsingBudget.limited(0)));
        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.appliedProfile()).isEqualTo(profile);
        assertThat(result.traces()).containsExactly(trace);
        assertThat(result.errorMessage()).contains("UNRESOLVED_LINES", "Limit");
        assertThat(result.parseSource()).isEqualTo(ParseSource.RULE);
    }

    // Verifies a missing API key is logged as a clean skip and never calls OpenRouter.
    @Test
    void missingApiKeySkipsFallbackWithoutCallingClient() {
        when(settingsService.current()).thenReturn(settings(""));

        ReceiptParseResult result = service.tryFallback(receipt(), ruleError(), options(AiParsingBudget.unlimited()));

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertLoggedStatus(AiParsingStatus.NO_API_KEY);
        verify(aiClient, never()).parseReceipt(any(), any());
    }

    // Verifies a disabled fallback remains explainable through ai_parsing_log.
    @Test
    void disabledFallbackIsLoggedAndSkipped() {
        when(settingsService.current()).thenReturn(new AiParsingSettings(
                false,
                "http://openrouter.test",
                "secret",
                "test/model",
                2500,
                0,
                new BigDecimal("0.900"),
                25,
                AiParsingTextMode.MINIMIZED,
                false));

        service.tryFallback(receipt(), ruleError(), options(AiParsingBudget.unlimited()));

        assertLoggedStatus(AiParsingStatus.DISABLED);
        verify(aiClient, never()).parseReceipt(any(), any());
    }

    // Verifies the per-sync AI call budget blocks automatic fallback after the configured limit.
    @Test
    void syncLimitIsEnforcedBeforeCallingClient() {
        when(settingsService.current()).thenReturn(settings("secret"));

        ReceiptParseResult result = service.tryFallback(receipt(), ruleError(), options(AiParsingBudget.limited(0)));

        assertThat(result.errorMessage()).contains("Limit");
        assertLoggedStatus(AiParsingStatus.SKIPPED_LIMIT);
        verify(aiClient, never()).parseReceipt(any(), any());
    }

    // Verifies FULL_TEXT manual reparse requires explicit confirmation before any receipt text can be sent.
    @Test
    void fullTextWithoutConfirmationIsRejectedBeforeCallingClient() {
        when(settingsService.current()).thenReturn(settings("secret"));

        ReceiptParseResult result = service.tryFallback(
                receipt(),
                ruleError(),
                new ParseExecutionOptions(
                        AiParsingTrigger.MANUAL_REPARSE_FORCE_FULL_TEXT,
                        true,
                        AiParsingTextMode.FULL_TEXT,
                        false,
                        AiParsingBudget.unlimited()));

        assertThat(result.errorMessage()).contains("FULL_TEXT");
        assertLoggedStatus(AiParsingStatus.FAILED);
        verify(aiClient, never()).parseReceipt(any(), any());
    }

    // Verifies valid, high-confidence AI JSON is adopted and records a successful prompt-free log.
    @Test
    void validAiResponseIsAdoptedAndLoggedAsSuccess() {
        when(settingsService.current()).thenReturn(settings("secret"));
        when(aiClient.parseReceipt(any(), any())).thenReturn(new AiReceiptParsingClientResponse(validJson(), "test/model", 10, 20, 30));

        ReceiptParseResult result = service.tryFallback(receipt(), ruleError(), options(AiParsingBudget.unlimited()));

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.parseSource()).isEqualTo(ParseSource.AI);
        assertLoggedStatus(AiParsingStatus.SUCCESS);
        verify(suggestionWriter).saveSuggestions(any(), any(), any(), any());
    }

    private ParseExecutionOptions options(AiParsingBudget budget) {
        return new ParseExecutionOptions(AiParsingTrigger.SYNC_AUTO, true, AiParsingTextMode.MINIMIZED, false, budget);
    }

    private Receipt receipt() {
        return new Receipt(123, "Unstrukturierter Bon\nSumme 2,50");
    }

    private ReceiptParseResult ruleError() {
        return new ReceiptParseResult(ParseStatus.PARSE_ERROR, null, "total_amount fehlt.");
    }

    private AiParsingSettings settings(String apiKey) {
        return new AiParsingSettings(
                true,
                "http://openrouter.test",
                apiKey,
                "test/model",
                2500,
                0,
                new BigDecimal("0.900"),
                25,
                AiParsingTextMode.MINIMIZED,
                false);
    }

    private void assertLoggedStatus(AiParsingStatus expectedStatus) {
        ArgumentCaptor<AiParsingLog> captor = ArgumentCaptor.forClass(AiParsingLog.class);
        verify(logRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(expectedStatus);
        assertThat(captor.getValue().getPromptSnippet()).isNull();
        assertThat(captor.getValue().getResponseSnippet()).isNull();
    }

    private String validJson() {
        return """
                {
                  "receiptDate": "2026-06-10",
                  "receiptTime": "10:15:00",
                  "storeName": "KI Markt",
                  "storeBranch": null,
                  "totalAmount": 2.50,
                  "currency": "EUR",
                  "bonusBalance": null,
                  "bonusPoints": null,
                  "bonusType": null,
                  "overallConfidence": 0.950,
                  "items": [
                    {
                      "positionIndex": 0,
                      "description": "KI Artikel",
                      "quantity": 1.0,
                      "unit": "Stk",
                      "unitPrice": 2.50,
                      "totalPrice": 2.50,
                      "discountAmount": null
                    }
                  ]
                }
                """;
    }
}
