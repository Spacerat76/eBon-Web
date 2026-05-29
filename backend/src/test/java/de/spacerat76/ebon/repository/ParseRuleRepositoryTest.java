package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.ParseRule;
import de.spacerat76.ebon.repository.ParseRuleRepository;
import de.spacerat76.ebon.web.dto.ParseRuleDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ParseRuleRepositoryTest {

    @Test
    void controllerCreateAndGet() {
        ParseRuleRepository repo = Mockito.mock(ParseRuleRepository.class);
        ParseRuleController controller = new ParseRuleController(repo);

        ParseRule saved = new ParseRule();
        saved.setId(1L);
        saved.setName("rule1");
        saved.setRegex("^ITEM$");

        Mockito.when(repo.save(Mockito.any(ParseRule.class))).thenReturn(saved);
        Mockito.when(repo.findById(1L)).thenReturn(Optional.of(saved));

        ParseRuleDto dto = new ParseRuleDto();
        dto.setName("rule1");
        dto.setRegex("^ITEM$");

        var resp = controller.create(dto);
        assertTrue(resp.getStatusCode().is2xxSuccessful());
        ParseRuleDto body = resp.getBody();
        assertNotNull(body);
        assertEquals(1L, body.getId());

        var getResp = controller.get(1L);
        assertTrue(getResp.getStatusCode().is2xxSuccessful());
        assertEquals("rule1", getResp.getBody().getName());
    }
}
