package de.ebon.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ReceiptUpdateRequest(
        LocalDate receiptDate,
        LocalTime receiptTime,
        @Size(max = 255)
        String storeName,
        @Size(max = 255)
        String storeBranch,
        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal totalAmount,
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency,
        @Digits(integer = 8, fraction = 2)
        BigDecimal bonusBalance,
        @Digits(integer = 8, fraction = 2)
        BigDecimal bonusPoints,
        @Size(max = 64)
        String bonusType,
        List<@Valid ReceiptItemUpdateRequest> items) {
}
