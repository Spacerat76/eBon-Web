package de.spacerat76.ebon.web;

import de.spacerat76.ebon.service.AiCategorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "AI operations")
public class AiController {

    private final AiCategorizationService aiService;

    public AiController(AiCategorizationService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/batch-categorize")
    @Operation(summary = "Run AI batch categorization for provided receipt IDs (or all if none)")
    public ResponseEntity<Map<String, Object>> batchCategorize(@RequestBody(required = false) List<Long> receiptIds) {
        int processed;
        if (receiptIds == null || receiptIds.isEmpty()) {
            processed = aiService.categorizeAllReceipts();
        } else {
            processed = aiService.categorizeReceipts(receiptIds);
        }
        return ResponseEntity.ok(Map.of("processed", processed));
    }
}
