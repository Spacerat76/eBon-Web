package de.spacerat76.ebon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.spacerat76.ebon.ai.AiClient;
import de.spacerat76.ebon.ai.AiParseResult;
import de.spacerat76.ebon.config.AppProperties;
import de.spacerat76.ebon.domain.AiCategorizationLog;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.repository.AiCategorizationLogRepository;
import de.spacerat76.ebon.repository.ReceiptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class AiCategorizationServiceTest {

    @Test
    void categorizeReceipt_createsLog() throws Exception {
        ReceiptRepository receiptRepository = Mockito.mock(ReceiptRepository.class);
        AiCategorizationLogRepository aiRepo = Mockito.mock(AiCategorizationLogRepository.class);
        AiClient aiClient = Mockito.mock(AiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AppProperties props = new AppProperties();
        props.setOpenrouterModel("test-model");

        Receipt r = new Receipt();
        r.setId(1L);
        r.setRawText("Chocolate Bar");

        Mockito.when(receiptRepository.findById(1L)).thenReturn(Optional.of(r));

        AiParseResult apr = new AiParseResult();
        apr.setStoreName("TestStore");
        Mockito.when(aiClient.parseReceipt(Mockito.anyString())).thenReturn(Optional.of(apr));

        AiCategorizationServiceImpl svc = new AiCategorizationServiceImpl(receiptRepository, aiRepo, aiClient, objectMapper, props);
        int processed = svc.categorizeReceipts(List.of(1L));
        assertEquals(1, processed);

        ArgumentCaptor<AiCategorizationLog> cap = ArgumentCaptor.forClass(AiCategorizationLog.class);
        Mockito.verify(aiRepo).save(cap.capture());
        AiCategorizationLog saved = cap.getValue();
        assertEquals(1L, saved.getReceiptId());
        assertTrue(saved.getResponsePayload().contains("TestStore"));
        assertEquals("test-model", saved.getModel());
    }
}
