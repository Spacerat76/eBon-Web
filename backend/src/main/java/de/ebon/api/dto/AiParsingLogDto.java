package de.ebon.api.dto;

import de.ebon.persistence.model.AiParsingStatus;
import de.ebon.persistence.model.AiParsingTrigger;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AiParsingLogDto(
        Long id,
        Long receiptId,
        AiParsingTrigger trigger,
        AiParsingStatus status,
        String modelUsed,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Integer durationMs,
        BigDecimal overallConfidence,
        String parseErrorBefore,
        String failureReason,
        Map<String, Object> fieldConfidence,
        List<String> warnings,
        String promptSnippet,
        String responseSnippet) {
}
