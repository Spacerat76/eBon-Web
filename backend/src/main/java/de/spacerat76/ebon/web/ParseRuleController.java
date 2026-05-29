package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.ParseRule;
import de.spacerat76.ebon.repository.ParseRuleRepository;
import de.spacerat76.ebon.web.dto.ParseRuleDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parse-rules")
@Tag(name = "Parse Rules", description = "Manage parse rules used by the parser")
public class ParseRuleController {

    private final ParseRuleRepository parseRuleRepository;

    public ParseRuleController(ParseRuleRepository parseRuleRepository) {
        this.parseRuleRepository = parseRuleRepository;
    }

    @GetMapping
    @Operation(summary = "List parse rules")
    public List<ParseRuleDto> list() {
        return parseRuleRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a parse rule")
    public ResponseEntity<ParseRuleDto> get(@PathVariable Long id) {
        Optional<ParseRule> opt = parseRuleRepository.findById(id);
        return opt.map(r -> ResponseEntity.ok(toDto(r))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create a parse rule")
    public ResponseEntity<ParseRuleDto> create(@RequestBody ParseRuleDto dto) {
        ParseRule r = new ParseRule();
        applyDtoToEntity(dto, r);
        r.setCreatedAt(OffsetDateTime.now());
        ParseRule saved = parseRuleRepository.save(r);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a parse rule")
    public ResponseEntity<ParseRuleDto> update(@PathVariable Long id, @RequestBody ParseRuleDto dto) {
        Optional<ParseRule> opt = parseRuleRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        ParseRule r = opt.get();
        applyDtoToEntity(dto, r);
        r.setUpdatedAt(OffsetDateTime.now());
        ParseRule saved = parseRuleRepository.save(r);
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a parse rule")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!parseRuleRepository.existsById(id)) return ResponseEntity.notFound().build();
        parseRuleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private ParseRuleDto toDto(ParseRule r) {
        ParseRuleDto d = new ParseRuleDto();
        d.setId(r.getId());
        d.setName(r.getName());
        d.setDescription(r.getDescription());
        d.setStoreNamePattern(r.getStoreNamePattern());
        d.setRegex(r.getRegex());
        d.setPriority(r.getPriority());
        d.setCreatedAt(r.getCreatedAt());
        d.setUpdatedAt(r.getUpdatedAt());
        return d;
    }

    private void applyDtoToEntity(ParseRuleDto dto, ParseRule r) {
        if (dto.getName() != null) r.setName(dto.getName());
        r.setDescription(dto.getDescription());
        r.setStoreNamePattern(dto.getStoreNamePattern());
        r.setRegex(dto.getRegex());
        if (dto.getPriority() != null) r.setPriority(dto.getPriority());
    }
}
