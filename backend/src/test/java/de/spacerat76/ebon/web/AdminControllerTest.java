package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.AiCategorizationLog;
import de.spacerat76.ebon.repository.AiCategorizationLogRepository;
import de.spacerat76.ebon.web.dto.AiCategorizationLogDto;
import de.spacerat76.ebon.web.dto.AppSettingDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class AdminControllerTest {

    @Test
    void listAiLogsAndSettings() {
        AiCategorizationLogRepository aiRepo = Mockito.mock(AiCategorizationLogRepository.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        AdminController ctrl = new AdminController(aiRepo, jdbcTemplate);

        AiCategorizationLog a = new AiCategorizationLog();
        a.setId(11L);
        a.setReceiptId(22L);
        a.setModel("test-model");
        a.setCost(new BigDecimal("0.1234"));
        a.setCreatedAt(OffsetDateTime.now());

        Mockito.when(aiRepo.findAll()).thenReturn(List.of(a));

        AppSettingDto sDto = new AppSettingDto();
        sDto.setKey("feature_x_enabled");
        sDto.setValue("true");
        sDto.setUpdatedAt(OffsetDateTime.now());

        Mockito.when(jdbcTemplate.query(Mockito.eq("SELECT key, value, updated_at FROM app_settings"), Mockito.any(RowMapper.class)))
            .thenReturn(List.of(sDto));

        Mockito.when(jdbcTemplate.query(Mockito.eq("SELECT key, value, updated_at FROM app_settings WHERE key = ?"), Mockito.any(Object[].class), Mockito.any(RowMapper.class)))
            .thenReturn(List.of(sDto));

        List<AiCategorizationLogDto> aiLogs = ctrl.listAiLogs();
        assertEquals(1, aiLogs.size());
        assertEquals(11L, aiLogs.get(0).getId());

        List<AppSettingDto> settings = ctrl.listSettings();
        assertEquals(1, settings.size());
        assertEquals("feature_x_enabled", settings.get(0).getKey());

        var resp = ctrl.getSetting("feature_x_enabled");
        assertTrue(resp.getStatusCode().is2xxSuccessful());
        assertEquals("true", resp.getBody().getValue());
    }
}
