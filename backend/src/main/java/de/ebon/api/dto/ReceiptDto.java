package de.ebon.api.dto;

import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ParseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record ReceiptDto(
        Long id,
        Integer paperlessDocumentId,
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
        String parseErrorMessage,
        OffsetDateTime deletedAt,
        DeleteReason deleteReason,
        List<ReceiptItemDto> items) {
}
