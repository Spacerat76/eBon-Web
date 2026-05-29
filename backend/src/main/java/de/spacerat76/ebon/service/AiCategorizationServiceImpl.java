package de.spacerat76.ebon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.spacerat76.ebon.ai.AiClient;
import de.spacerat76.ebon.ai.AiParseResult;
import de.spacerat76.ebon.config.AppProperties;
import de.spacerat76.ebon.domain.AiCategorizationLog;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.repository.AiCategorizationLogRepository;
import de.spacerat76.ebon.repository.ReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AiCategorizationServiceImpl implements AiCategorizationService {

    private final ReceiptRepository receiptRepository;
    private final AiCategorizationLogRepository aiCategorizationLogRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    @Autowired
    public AiCategorizationServiceImpl(ReceiptRepository receiptRepository,
                                       AiCategorizationLogRepository aiCategorizationLogRepository,
                                       AiClient aiClient,
                                       ObjectMapper objectMapper,
                                       AppProperties props) {
        this.receiptRepository = receiptRepository;
        this.aiCategorizationLogRepository = aiCategorizationLogRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public int categorizeReceipts(List<Long> receiptIds) {
        int processed = 0;
        for (Long rid : receiptIds) {
            Optional<Receipt> or = receiptRepository.findById(rid);
            if (or.isEmpty()) continue;
            Receipt r = or.get();

            String requestPayload;
            try {
                requestPayload = objectMapper.writeValueAsString(Map.of("receiptId", r.getId(), "rawText", r.getRawText()));
            } catch (Exception e) {
                requestPayload = "{\"error\":\"serialize_request_failed\"}";
            }

            String responsePayload;
            Optional<AiParseResult> res = Optional.empty();
            try {
                res = aiClient.parseReceipt(r.getRawText());
                if (res.isPresent()) {
                    responsePayload = objectMapper.writeValueAsString(res.get());
                } else {
                    responsePayload = "{\"result\":null}";
                }
            } catch (Exception ex) {
                responsePayload = "{\"error\":\"ai_client_failed\",\"message\":\"" + ex.getMessage() + "\"}";
            }

            AiCategorizationLog log = new AiCategorizationLog();
            log.setReceiptId(r.getId());
            log.setRequestPayload(requestPayload);
            log.setResponsePayload(responsePayload);
            log.setModel(props.getOpenrouterModel());
            // If AI returned a cost estimate, persist it
            if (res.isPresent() && res.get().getCost() != null) {
                log.setCost(res.get().getCost());
            }
            log.setCreatedAt(java.time.OffsetDateTime.now());

            aiCategorizationLogRepository.save(log);
            processed++;
        }
        return processed;
    }

    @Override
    public int categorizeAllReceipts() {
        List<Long> ids = receiptRepository.findAll().stream().map(Receipt::getId).collect(Collectors.toList());
        return categorizeReceipts(ids);
    }
}
