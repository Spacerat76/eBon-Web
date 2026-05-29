package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.AiCategorizationLog;
import de.spacerat76.ebon.repository.AiCategorizationLogRepository;
import de.spacerat76.ebon.web.dto.AiCategorizationLogDto;
import de.spacerat76.ebon.web.dto.AppSettingDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin read endpoints for AI logs and runtime settings")
public class AdminController {

    private final AiCategorizationLogRepository aiRepo;
    private final JdbcTemplate jdbcTemplate;

    public AdminController(AiCategorizationLogRepository aiRepo, JdbcTemplate jdbcTemplate) {
        this.aiRepo = aiRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/ai-categorization-logs")
    @Operation(summary = "List AI categorization logs")
    public List<AiCategorizationLogDto> listAiLogs() {
        return aiRepo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/app-settings")
    @Operation(summary = "List application settings")
    public List<AppSettingDto> listSettings() {
        String sql = "SELECT key, value, updated_at FROM app_settings";
        RowMapper<AppSettingDto> rm = (rs, rowNum) -> {
            AppSettingDto d = new AppSettingDto();
            d.setKey(rs.getString("key"));
            d.setValue(rs.getString("value"));
            d.setUpdatedAt(rs.getObject("updated_at", java.time.OffsetDateTime.class));
            return d;
        };
        return jdbcTemplate.query(sql, rm);
    }

    @GetMapping("/app-settings/{key}")
    @Operation(summary = "Get a single application setting by key")
    public ResponseEntity<AppSettingDto> getSetting(@PathVariable String key) {
        String sql = "SELECT key, value, updated_at FROM app_settings WHERE key = ?";
        RowMapper<AppSettingDto> rm = (rs, rowNum) -> {
            AppSettingDto d = new AppSettingDto();
            d.setKey(rs.getString("key"));
            d.setValue(rs.getString("value"));
            d.setUpdatedAt(rs.getObject("updated_at", java.time.OffsetDateTime.class));
            return d;
        };
        java.util.List<AppSettingDto> list = jdbcTemplate.query(sql, new Object[]{key}, rm);
        if (list.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(list.get(0));
    }

    private AiCategorizationLogDto toDto(AiCategorizationLog a) {
        AiCategorizationLogDto d = new AiCategorizationLogDto();
        d.setId(a.getId());
        d.setReceiptId(a.getReceiptId());
        d.setRequestPayload(a.getRequestPayload());
        d.setResponsePayload(a.getResponsePayload());
        d.setModel(a.getModel());
        d.setCost(a.getCost());
        d.setCreatedAt(a.getCreatedAt());
        return d;
    }

    
}
