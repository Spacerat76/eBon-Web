package de.ebon.api.dto;

import de.ebon.sync.SyncStatusDto;
import java.math.BigDecimal;
import java.util.List;

public record DashboardDto(
        BigDecimal currentMonthTotal,
        BigDecimal previousMonthTotal,
        BigDecimal currentYearTotal,
        List<ReportDto.ByCategory> currentMonthByCategory,
        List<ReportDto.Bonus> bonusSummary,
        List<ReceiptDto> recentReceipts,
        long uncategorizedItemsCount,
        SyncStatusDto lastSyncStatus) {
}
