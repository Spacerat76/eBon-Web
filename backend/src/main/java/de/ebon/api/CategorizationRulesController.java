package de.ebon.api;

import de.ebon.api.dto.CategorizationRuleApplyResponse;
import de.ebon.api.dto.CategorizationRuleDto;
import de.ebon.api.dto.CategorizationRulePreviewRequest;
import de.ebon.api.dto.CategorizationRulePreviewResponse;
import de.ebon.api.dto.CategorizationRuleRequest;
import de.ebon.categorization.CategorizationRuleManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Kategorisierungsregeln")
@SecurityRequirement(name = "bearerAuth")
public class CategorizationRulesController {

    private final CategorizationRuleManagementService ruleService;

    public CategorizationRulesController(CategorizationRuleManagementService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping("/api/categorization-rules")
    @Operation(summary = "Regeln nach Prioritaet abrufen")
    public List<CategorizationRuleDto> listRules() {
        return ruleService.list();
    }

    @PostMapping("/api/categorization-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Regel anlegen")
    public CategorizationRuleDto createRule(@Valid @RequestBody CategorizationRuleRequest request) {
        return ruleService.create(request);
    }

    @PutMapping("/api/categorization-rules/{id}")
    @Operation(summary = "Regel aktualisieren")
    public CategorizationRuleDto updateRule(
            @PathVariable Long id,
            @Valid @RequestBody CategorizationRuleRequest request) {
        return ruleService.update(id, request);
    }

    @DeleteMapping("/api/categorization-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Regel loeschen")
    public void deleteRule(@PathVariable Long id) {
        ruleService.delete(id);
    }

    @PostMapping("/api/categorization-rules/{id}/apply")
    @Operation(summary = "Regel auf bestehende Daten anwenden")
    public CategorizationRuleApplyResponse applyRule(@PathVariable Long id) {
        return new CategorizationRuleApplyResponse(ruleService.apply(id));
    }

    @PostMapping("/api/categorization-rules/preview")
    @Operation(summary = "Regelvorschau fuer bestehende Daten")
    public CategorizationRulePreviewResponse previewRule(
            @Valid @RequestBody CategorizationRulePreviewRequest request) {
        return new CategorizationRulePreviewResponse(ruleService.preview(request));
    }
}
