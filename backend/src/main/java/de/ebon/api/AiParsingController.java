package de.ebon.api;

import de.ebon.api.dto.AiParsingLogDto;
import de.ebon.api.dto.FixtureExportDto;
import de.ebon.api.dto.FixturePreviewDto;
import de.ebon.api.dto.FixturePreviewRequest;
import de.ebon.api.dto.MigrationDraftDto;
import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ParseRuleSuggestionAcceptRequest;
import de.ebon.api.dto.ParseRuleSuggestionDto;
import de.ebon.api.dto.ParseRuleSuggestionRejectRequest;
import de.ebon.api.dto.ParseRuleSuggestionUpdateRequest;
import de.ebon.api.service.AiParsingApiService;
import de.ebon.persistence.model.ParseRuleSuggestionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "AI Parsing")
@SecurityRequirement(name = "bearerAuth")
public class AiParsingController {

    private final AiParsingApiService aiParsingApiService;

    public AiParsingController(AiParsingApiService aiParsingApiService) {
        this.aiParsingApiService = aiParsingApiService;
    }

    @GetMapping("/api/receipts/{id}/ai-parsing-log")
    @Operation(summary = "KI-Parsing-Logs fuer einen Bon abrufen")
    public List<AiParsingLogDto> receiptAiParsingLog(@PathVariable Long id) {
        return aiParsingApiService.logsForReceipt(id);
    }

    @GetMapping("/api/parser/rule-suggestions")
    @Operation(summary = "Parser-Regelvorschlaege paginiert abrufen")
    public PageResponse<ParseRuleSuggestionDto> listRuleSuggestions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ParseRuleSuggestionStatus status,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) String validationStatus) {
        return aiParsingApiService.listSuggestions(page, size, status, store, validationStatus);
    }

    @GetMapping("/api/parser/rule-suggestions/{id}")
    @Operation(summary = "Parser-Regelvorschlag abrufen")
    public ParseRuleSuggestionDto getRuleSuggestion(@PathVariable Long id) {
        return aiParsingApiService.getSuggestion(id);
    }

    @PutMapping("/api/parser/rule-suggestions/{id}")
    @Operation(summary = "Parser-Regelvorschlag bearbeiten")
    public ParseRuleSuggestionDto updateRuleSuggestion(
            @PathVariable Long id,
            @Valid @RequestBody ParseRuleSuggestionUpdateRequest request) {
        return aiParsingApiService.updateSuggestion(id, request);
    }

    @PostMapping("/api/parser/rule-suggestions/{id}/accept")
    @Operation(summary = "Parser-Regelvorschlag als aktive parse_rule uebernehmen")
    public ParseRuleSuggestionDto acceptRuleSuggestion(
            @PathVariable Long id,
            @Valid @RequestBody ParseRuleSuggestionAcceptRequest request) {
        return aiParsingApiService.acceptSuggestion(id, request);
    }

    @PostMapping("/api/parser/rule-suggestions/{id}/reject")
    @Operation(summary = "Parser-Regelvorschlag ablehnen")
    public ParseRuleSuggestionDto rejectRuleSuggestion(
            @PathVariable Long id,
            @Valid @RequestBody ParseRuleSuggestionRejectRequest request) {
        return aiParsingApiService.rejectSuggestion(id, request);
    }

    @PostMapping("/api/parser/rule-suggestions/export-migration")
    @Operation(summary = "Akzeptierte Parser-Regeln als Flyway-Migrationsentwurf exportieren")
    public MigrationDraftDto exportAcceptedRuleMigration() {
        return aiParsingApiService.exportAcceptedMigration();
    }

    @PostMapping("/api/parser/fixtures/preview")
    @Operation(summary = "Anonymisierte Fixture-Vorschau erzeugen")
    public FixturePreviewDto fixturePreview(@Valid @RequestBody FixturePreviewRequest request) {
        return aiParsingApiService.fixturePreview(request.aiParsingLogId());
    }

    @PostMapping("/api/parser/fixtures/export")
    @Operation(summary = "Anonymisiertes Fixture lokal ausserhalb des Corpus exportieren")
    public FixtureExportDto fixtureExport(@Valid @RequestBody FixturePreviewRequest request) {
        return aiParsingApiService.fixtureExport(request.aiParsingLogId());
    }
}
