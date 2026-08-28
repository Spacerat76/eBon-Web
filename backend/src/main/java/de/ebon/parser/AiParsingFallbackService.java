package de.ebon.parser;

import de.ebon.persistence.model.AiParsingLog;
import de.ebon.persistence.model.AiParsingStatus;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.repository.AiParsingLogRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiParsingFallbackService {

    private static final int SNIPPET_LIMIT = 500;

    private final AiParsingSettingsService settingsService;
    private final AiReceiptParsingClient aiClient;
    private final AiReceiptJsonParser jsonParser;
    private final AiParsingLogRepository logRepository;
    private final ParseRuleSuggestionWriter suggestionWriter;
    private final ObjectMapper objectMapper;

    public AiParsingFallbackService(
            AiParsingSettingsService settingsService,
            AiReceiptParsingClient aiClient,
            AiReceiptJsonParser jsonParser,
            AiParsingLogRepository logRepository,
            ParseRuleSuggestionWriter suggestionWriter,
            ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.aiClient = aiClient;
        this.jsonParser = jsonParser;
        this.logRepository = logRepository;
        this.suggestionWriter = suggestionWriter;
        this.objectMapper = objectMapper;
    }

    public AiParsingBudget newSyncBudget() {
        return AiParsingBudget.limited(settingsService.current().syncCallLimit());
    }

    public ReceiptParseResult tryFallback(Receipt receipt, ReceiptParseResult ruleResult, ParseExecutionOptions options) {
        AiParsingSettings settings = settingsService.current();
        AiParsingLog log = new AiParsingLog(receipt, options.trigger(), ruleResult.errorMessage());

        if (!options.useAiFallback()) {
            finish(log, AiParsingStatus.DISABLED, settings, "KI-Parsing-Fallback wurde fuer diesen Lauf deaktiviert.", null, null, null);
            return ruleResult;
        }
        if (!settings.fallbackEnabled()) {
            finish(log, AiParsingStatus.DISABLED, settings, "KI-Parsing-Fallback ist in den Einstellungen deaktiviert.", null, null, null);
            return ruleResult;
        }
        if (!settings.hasApiKey()) {
            finish(log, AiParsingStatus.NO_API_KEY, settings, "OpenRouter API-Key fehlt.", null, null, null);
            return ruleResult;
        }

        AiParsingTextMode textMode = options.requestedTextMode() == null
                ? settings.textMode()
                : options.requestedTextMode();
        if (textMode == AiParsingTextMode.FULL_TEXT && !options.fullTextConfirmed()) {
            finish(log, AiParsingStatus.FAILED, settings, "FULL_TEXT-Reparse wurde nicht ausdruecklich bestaetigt.", null, null, null);
            return withAiError(ruleResult, "FULL_TEXT-Reparse wurde nicht ausdruecklich bestaetigt.");
        }
        if (!options.budget().tryAcquire()) {
            finish(log, AiParsingStatus.SKIPPED_LIMIT, settings, "KI-Parsing-Sync-Limit wurde erreicht.", null, null, null);
            return withAiError(ruleResult, "KI-Parsing-Sync-Limit wurde erreicht.");
        }

        String rawTextForModel = textMode == AiParsingTextMode.FULL_TEXT
                ? receipt.getRawText()
                : minimizeText(receipt.getRawText());
        AiReceiptParsingPrompt prompt = new AiReceiptParsingPrompt(
                rawTextForModel,
                partialJson(ruleResult),
                ruleResult.errorMessage(),
                textMode);

        try {
            AiReceiptParsingClientResponse response = aiClient.parseReceipt(prompt, settings);
            AiReceiptJsonParseResult parsed = jsonParser.parseWithMetadata(response.content(), settings.minConfidence());
            AiParsingStatus status = aiStatus(parsed.parseResult());
            AiParsingLog savedLog = finish(
                    log,
                    status,
                    settings,
                    parsed.parseResult().parsed() ? null : parsed.parseResult().errorMessage(),
                    parsed,
                    settings.storeDebugSnippets() ? snippet(rawTextForModel) : null,
                    settings.storeDebugSnippets() ? snippet(response.content()) : null,
                    response);
            suggestionWriter.saveSuggestions(savedLog, receipt, parsed.ruleSuggestions(), receipt.getRawText());

            if (parsed.parseResult().parsed()) {
                return parsed.parseResult();
            }
            return withAiError(ruleResult, parsed.parseResult().errorMessage());
        } catch (RestClientException exception) {
            finish(log, AiParsingStatus.FAILED, settings, "OpenRouter KI-Parsing konnte nicht ausgefuehrt werden.", null, null, null);
            return withAiError(ruleResult, "OpenRouter KI-Parsing konnte nicht ausgefuehrt werden.");
        } catch (RuntimeException exception) {
            finish(log, AiParsingStatus.FAILED, settings, "KI-Parsing-Fallback ist fehlgeschlagen.", null, null, null);
            return withAiError(ruleResult, "KI-Parsing-Fallback ist fehlgeschlagen.");
        }
    }

    private AiParsingStatus aiStatus(ReceiptParseResult result) {
        if (result.parsed()) {
            return AiParsingStatus.SUCCESS;
        }
        String message = result.errorMessage() == null ? "" : result.errorMessage();
        if (message.contains("Konfidenz")) {
            return AiParsingStatus.LOW_CONFIDENCE;
        }
        return AiParsingStatus.INVALID_RESPONSE;
    }

    private AiParsingLog finish(
            AiParsingLog log,
            AiParsingStatus status,
            AiParsingSettings settings,
            String failureReason,
            AiReceiptJsonParseResult parsed,
            String promptSnippet,
            String responseSnippet) {
        return finish(log, status, settings, failureReason, parsed, promptSnippet, responseSnippet, null);
    }

    private AiParsingLog finish(
            AiParsingLog log,
            AiParsingStatus status,
            AiParsingSettings settings,
            String failureReason,
            AiReceiptJsonParseResult parsed,
            String promptSnippet,
            String responseSnippet,
            AiReceiptParsingClientResponse response) {
        log.finish(
                status,
                settings.model(),
                failureReason,
                parsed == null ? null : parsed.overallConfidence(),
                parsed == null ? null : parsed.fieldConfidenceJson(),
                parsed == null ? null : parsed.warningsJson(),
                response == null ? null : response.promptTokens(),
                response == null ? null : response.completionTokens(),
                response == null ? null : response.totalTokens(),
                promptSnippet,
                responseSnippet);
        return logRepository.saveAndFlush(log);
    }

    private ReceiptParseResult withAiError(ReceiptParseResult ruleResult, String message) {
        String fallbackError = message == null || message.isBlank()
                ? "KI-Parsing-Fallback konnte den Bon nicht valide uebernehmen." : message;
        return new ReceiptParseResult(
                ParseStatus.PARSE_ERROR,
                ruleResult.receipt(),
                ruleResult.errorMessage() == null || ruleResult.errorMessage().isBlank() ? fallbackError
                        : ruleResult.errorMessage() + " | " + fallbackError,
                ruleResult.parseSource(), ruleResult.appliedProfile(), ruleResult.traces());
    }

    private String partialJson(ReceiptParseResult ruleResult) {
        try {
            return objectMapper.writeValueAsString(ruleResult.receipt());
        } catch (RuntimeException exception) {
            return "{}";
        }
    }

    private String minimizeText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        return rawText.lines()
                .filter(line -> !isNoiseLine(line))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private boolean isNoiseLine(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.contains("TSE")
                || upper.contains("FISKAL")
                || upper.contains("TERMINAL")
                || upper.contains("TRACE")
                || upper.contains("SERIENNUMMER")
                || upper.contains("KARTENZAHLUNG")
                || upper.contains("GIROCARD")
                || upper.contains("MASTERCARD")
                || upper.contains("VISA")
                || upper.contains("UST-ID")
                || upper.contains("STEUER")
                || upper.matches("[-=* ]{5,}");
    }

    private String snippet(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String masked = value
                .replaceAll("(?i)(Authorization:\\s*Bearer\\s+)[^\\s]+", "$1********")
                .replaceAll("(?i)(api[-_ ]?key\\s*[:=]\\s*)[^\\s]+", "$1********")
                .replaceAll("\\b\\d{8,}\\b", "********");
        return masked.length() <= SNIPPET_LIMIT ? masked : masked.substring(0, SNIPPET_LIMIT);
    }
}
