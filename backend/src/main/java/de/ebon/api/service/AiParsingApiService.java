package de.ebon.api.service;

import de.ebon.api.dto.AiParsingLogDto;
import de.ebon.api.dto.FixtureExportDto;
import de.ebon.api.dto.FixturePreviewDto;
import de.ebon.api.dto.MigrationDraftDto;
import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ParseRuleSuggestionAcceptRequest;
import de.ebon.api.dto.ParseRuleSuggestionDto;
import de.ebon.api.dto.ParseRuleSuggestionRejectRequest;
import de.ebon.api.dto.ParseRuleSuggestionUpdateRequest;
import de.ebon.categorization.CategorizationService;
import de.ebon.parser.AiParsingTextMode;
import de.ebon.parser.ParseExecutionOptions;
import de.ebon.parser.ParseRuleSuggestionValidator;
import de.ebon.parser.ReceiptParseApplier;
import de.ebon.parser.ReceiptParseResult;
import de.ebon.parser.ReceiptParserService;
import de.ebon.persistence.model.AiParsingLog;
import de.ebon.persistence.model.ParseRule;
import de.ebon.persistence.model.ParseRuleSuggestion;
import de.ebon.persistence.model.ParseRuleSuggestionStatus;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.RuleSource;
import de.ebon.persistence.repository.AiParsingLogRepository;
import de.ebon.persistence.repository.ParseRuleRepository;
import de.ebon.persistence.repository.ParseRuleSuggestionRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiParsingApiService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final AiParsingLogRepository logRepository;
    private final ParseRuleSuggestionRepository suggestionRepository;
    private final ParseRuleRepository parseRuleRepository;
    private final ReceiptRepository receiptRepository;
    private final ReceiptParserService receiptParserService;
    private final ReceiptParseApplier receiptParseApplier;
    private final CategorizationService categorizationService;
    private final ParseRuleSuggestionValidator suggestionValidator;
    private final ObjectMapper objectMapper;

    public AiParsingApiService(
            AiParsingLogRepository logRepository,
            ParseRuleSuggestionRepository suggestionRepository,
            ParseRuleRepository parseRuleRepository,
            ReceiptRepository receiptRepository,
            ReceiptParserService receiptParserService,
            ReceiptParseApplier receiptParseApplier,
            CategorizationService categorizationService,
            ParseRuleSuggestionValidator suggestionValidator,
            ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.suggestionRepository = suggestionRepository;
        this.parseRuleRepository = parseRuleRepository;
        this.receiptRepository = receiptRepository;
        this.receiptParserService = receiptParserService;
        this.receiptParseApplier = receiptParseApplier;
        this.categorizationService = categorizationService;
        this.suggestionValidator = suggestionValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AiParsingLogDto> logsForReceipt(Long receiptId) {
        return logRepository.findByReceipt_IdOrderByStartedAtDesc(receiptId).stream()
                .map(this::toLogDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ParseRuleSuggestionDto> listSuggestions(
            int page,
            int size,
            ParseRuleSuggestionStatus status,
            String store,
            String validationStatus) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(suggestionRepository.findAll(
                suggestionSpec(status, store, validationStatus),
                pageable).map(this::toSuggestionDto), "createdAt", "desc");
    }

    @Transactional(readOnly = true)
    public ParseRuleSuggestionDto getSuggestion(Long id) {
        return toSuggestionDto(suggestion(id));
    }

    @Transactional
    public ParseRuleSuggestionDto updateSuggestion(Long id, ParseRuleSuggestionUpdateRequest request) {
        ParseRuleSuggestion suggestion = suggestion(id);
        ensureOpen(suggestion);
        applyDraft(suggestion, request);
        return toSuggestionDto(suggestionRepository.saveAndFlush(suggestion));
    }

    @Transactional
    public ParseRuleSuggestionDto acceptSuggestion(Long id, ParseRuleSuggestionAcceptRequest request) {
        ParseRuleSuggestion suggestion = suggestion(id);
        ensureOpen(suggestion);
        if (request.suggestion() != null) {
            applyDraft(suggestion, request.suggestion());
        }
        if (suggestion.getValidationStatus() != de.ebon.persistence.model.ParseRuleValidationStatus.VALID) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(422),
                    "Nur valide Parser-Regelvorschlaege koennen akzeptiert werden.");
        }

        ParseRule parseRule = new ParseRule(
                suggestion.getStoreName(),
                suggestion.getRuleType(),
                suggestion.getMatchRegex(),
                suggestion.getExtractGroup(),
                RuleSource.AI_ADAPTED);
        parseRule.updateConfidence(suggestion.getConfidence());
        parseRule = parseRuleRepository.saveAndFlush(parseRule);
        suggestion.accept(parseRule);
        ParseRuleSuggestion saved = suggestionRepository.saveAndFlush(suggestion);
        reparseScope(saved, request.reparseScope());
        return toSuggestionDto(saved);
    }

    @Transactional
    public ParseRuleSuggestionDto rejectSuggestion(Long id, ParseRuleSuggestionRejectRequest request) {
        ParseRuleSuggestion suggestion = suggestion(id);
        ensureOpen(suggestion);
        suggestion.reject(request.rejectionReason());
        return toSuggestionDto(suggestionRepository.saveAndFlush(suggestion));
    }

    @Transactional(readOnly = true)
    public MigrationDraftDto exportAcceptedMigration() {
        List<ParseRuleSuggestion> suggestions = suggestionRepository
                .findByStatusAndAcceptedParseRuleIsNotNullOrderByIdAsc(ParseRuleSuggestionStatus.ACCEPTED);
        StringBuilder sql = new StringBuilder();
        sql.append("-- Generated draft. Review manually before committing.\n");
        for (ParseRuleSuggestion suggestion : suggestions) {
            ParseRule rule = suggestion.getAcceptedParseRule();
            sql.append("INSERT INTO parse_rule (store_name, rule_type, match_regex, extract_group, confidence, source, is_active) VALUES (")
                    .append(sqlValue(rule.getStoreName())).append(", ")
                    .append(sqlValue(rule.getRuleType().name())).append(", ")
                    .append(sqlValue(rule.getMatchRegex())).append(", ")
                    .append(sqlValue(rule.getExtractGroup())).append(", ")
                    .append(rule.getConfidence() == null ? "NULL" : rule.getConfidence().toPlainString()).append(", ")
                    .append("'AI_ADAPTED', TRUE)\n")
                    .append("ON CONFLICT DO NOTHING;\n");
        }
        return new MigrationDraftDto(
                "V_next__add_ai_adapted_parse_rules_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".sql",
                sql.toString());
    }

    @Transactional(readOnly = true)
    public FixturePreviewDto fixturePreview(Long aiParsingLogId) {
        AiParsingLog log = logRepository.findById(aiParsingLogId)
                .orElseThrow(() -> new EntityNotFoundException("KI-Parsing-Log nicht gefunden."));
        Receipt receipt = log.getReceipt();
        if (receipt == null) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(422),
                    "KI-Parsing-Log ist keinem Bon zugeordnet.");
        }
        String baseName = "ai_parsing_receipt_" + receipt.getPaperlessDocumentId();
        return new FixturePreviewDto(
                baseName,
                anonymize(receipt.getRawText()),
                expectedJson(receipt));
    }

    public FixtureExportDto fixtureExport(Long aiParsingLogId) {
        FixturePreviewDto preview = fixturePreview(aiParsingLogId);
        Path directory = Path.of("build", "generated-fixtures").toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            Path textFile = directory.resolve(preview.suggestedBaseName() + ".txt");
            Path expectedFile = directory.resolve(preview.suggestedBaseName() + ".expected.json");
            Files.writeString(textFile, preview.receiptText());
            Files.writeString(expectedFile, preview.expectedJson());
            return new FixtureExportDto(directory.toString(), textFile.getFileName().toString(), expectedFile.getFileName().toString());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void applyDraft(ParseRuleSuggestion suggestion, ParseRuleSuggestionUpdateRequest request) {
        Receipt receipt = suggestion.getReceipt();
        ParseRuleSuggestionValidator.ValidationResult validation = suggestionValidator.validate(
                receipt == null ? "" : receipt.getRawText(),
                request.ruleType(),
                request.matchRegex(),
                request.extractGroup());
        suggestion.updateDraft(
                blankToNull(request.storeName()),
                request.ruleType(),
                request.matchRegex(),
                blankToNull(request.extractGroup()),
                request.confidence(),
                request.problemDescription(),
                request.solutionRationale(),
                validation.status(),
                validation.message());
    }

    private void reparseScope(ParseRuleSuggestion suggestion, ParseRuleSuggestionAcceptRequest.ReparseScope scope) {
        List<Receipt> receipts = switch (scope) {
            case NONE -> List.of();
            case CURRENT_RECEIPT -> suggestion.getReceipt() == null ? List.of() : List.of(suggestion.getReceipt());
            case PARSE_ERROR_BY_STORE -> suggestion.getStoreName() == null
                    ? List.of()
                    : receiptRepository.findByDeletedAtIsNullAndParseStatusAndStoreNameIgnoreCase(ParseStatus.PARSE_ERROR, suggestion.getStoreName());
            case ALL_PARSE_ERROR -> receiptRepository.findByDeletedAtIsNullAndParseStatus(ParseStatus.PARSE_ERROR);
        };
        for (Receipt receipt : receipts) {
            ReceiptParseResult parseResult = receiptParserService.parse(
                    receipt,
                    ParseExecutionOptions.manual(false, AiParsingTextMode.MINIMIZED, false));
            receipt.clearItems();
            receiptParseApplier.apply(receipt, parseResult);
            receiptRepository.saveAndFlush(receipt);
            categorizationService.categorizeReceipt(receipt.getId());
        }
    }

    private ParseRuleSuggestion suggestion(Long id) {
        return suggestionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Parser-Regelvorschlag nicht gefunden."));
    }

    private void ensureOpen(ParseRuleSuggestion suggestion) {
        if (suggestion.getStatus() != ParseRuleSuggestionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parser-Regelvorschlag ist nicht mehr offen.");
        }
    }

    private Specification<ParseRuleSuggestion> suggestionSpec(
            ParseRuleSuggestionStatus status,
            String store,
            String validationStatus) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (store != null && !store.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("storeName")), "%" + store.toLowerCase().trim() + "%"));
            }
            if (validationStatus != null && !validationStatus.isBlank()) {
                predicates.add(builder.equal(root.get("validationStatus"), validationStatus));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AiParsingLogDto toLogDto(AiParsingLog log) {
        return new AiParsingLogDto(
                log.getId(),
                log.getReceipt() == null ? null : log.getReceipt().getId(),
                log.getTrigger(),
                log.getStatus(),
                log.getModelUsed(),
                log.getStartedAt(),
                log.getFinishedAt(),
                log.getDurationMs(),
                log.getOverallConfidence(),
                log.getParseErrorBefore(),
                log.getFailureReason(),
                readMap(log.getFieldConfidenceJson()),
                readStringList(log.getWarningsJson()),
                log.getPromptSnippet(),
                log.getResponseSnippet());
    }

    private ParseRuleSuggestionDto toSuggestionDto(ParseRuleSuggestion suggestion) {
        return new ParseRuleSuggestionDto(
                suggestion.getId(),
                suggestion.getReceipt() == null ? null : suggestion.getReceipt().getId(),
                suggestion.getAiParsingLog().getId(),
                suggestion.getStoreName(),
                suggestion.getRuleType(),
                suggestion.getMatchRegex(),
                suggestion.getExtractGroup(),
                suggestion.getConfidence(),
                suggestion.getTrigger(),
                suggestion.getProblemDescription(),
                suggestion.getSolutionRationale(),
                suggestion.getValidationStatus(),
                suggestion.getValidationMessage(),
                suggestion.getStatus(),
                suggestion.getRejectionReason(),
                suggestion.getAcceptedParseRule() == null ? null : suggestion.getAcceptedParseRule().getId());
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private String expectedJson(Receipt receipt) {
        try {
            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("receiptDate", receipt.getReceiptDate());
            expected.put("receiptTime", receipt.getReceiptTime());
            expected.put("storeName", anonymize(receipt.getStoreName()));
            expected.put("storeBranch", anonymize(receipt.getStoreBranch()));
            expected.put("totalAmount", receipt.getTotalAmount());
            expected.put("currency", receipt.getCurrency());
            expected.put("bonusBalance", receipt.getBonusBalance());
            expected.put("bonusPoints", receipt.getBonusPoints());
            expected.put("bonusType", receipt.getBonusType());
            expected.put("items", receipt.getItems().stream()
                    .map(item -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("positionIndex", item.getPositionIndex());
                        entry.put("description", anonymize(item.getDescription()));
                        entry.put("totalPrice", item.getTotalPrice());
                        return entry;
                    })
                    .toList());
            return objectMapper.writeValueAsString(expected);
        } catch (RuntimeException exception) {
            return "{}";
        }
    }

    private String anonymize(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("\\b\\d{5}\\b", "12345")
                .replaceAll("\\b\\d{3,}\\b", "123")
                .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "user@example.invalid")
                .replaceAll("(?i)([A-ZÄÖÜ][a-zäöüß]+\\s+(?:strasse|straße|weg|platz|allee|ring)\\s*)\\d+", "$1 1");
    }

    private String sqlValue(String value) {
        return value == null || value.isBlank() ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
