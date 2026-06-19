package de.ebon.api.dto;

import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.ParseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ParseRuleSuggestionReceiptContextDto(
        Long receiptId,
        Integer paperlessDocumentId,
        String rawText,
        ParseStatus parseStatus,
        ParseSource parseSource,
        LocalDate receiptDate,
        LocalTime receiptTime,
        String storeName,
        String storeBranch,
        BigDecimal totalAmount,
        String currency,
        List<ParseRuleSuggestionItemDto> items) {
}
