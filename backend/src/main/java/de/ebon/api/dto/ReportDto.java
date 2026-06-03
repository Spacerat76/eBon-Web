package de.ebon.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReportDto {

    private ReportDto() {
    }

    public record ByCategory(Long categoryId, String categoryName, BigDecimal total) {
    }

    public record ByPeriod(LocalDate periodStart, String period, BigDecimal total) {
    }

    public record ByStore(String storeName, BigDecimal total, long receiptCount) {
    }

    public record TopItem(String description, BigDecimal total, long count) {
    }

    public record Bonus(String bonusType, BigDecimal totalPoints, BigDecimal totalEarnedBalance) {
    }
}
