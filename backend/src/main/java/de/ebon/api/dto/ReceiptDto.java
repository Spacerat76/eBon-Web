package de.ebon.api.dto;

import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.ParseSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record ReceiptDto(
        Long id,
        Integer paperlessDocumentId,
        String paperlessDocumentUrl,
        OffsetDateTime importedAt,
        LocalDate receiptDate,
        LocalTime receiptTime,
        String storeName,
        String storeBranch,
        BigDecimal totalAmount,
        String currency,
        BigDecimal bonusBalance,
        BigDecimal bonusPoints,
        String bonusType,
        ParseStatus parseStatus,
        ParseSource parseSource,
        String parseErrorMessage,
        Long formatProfileId,
        Integer formatProfileVersion,
        long unresolvedLineCount,
        AiParsingSummaryDto aiParsingSummary,
        OffsetDateTime deletedAt,
        DeleteReason deleteReason,
        String rawText,
        List<ReceiptItemDto> items) {
}
