package de.ebon.api.service;

import de.ebon.api.dto.DashboardDto;
import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ReportDto;
import de.ebon.api.dto.SearchResultDto;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.SyncStatus;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.sync.PaperlessSyncService;
import de.ebon.sync.SyncStatusDto;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "app.sync.scheduler.enabled=false")
class QueryApiServiceTests extends PostgresIntegrationTestSupport {

    @Autowired
    private QueryApiService queryApiService;

    @Autowired
    private ReceiptApiService receiptApiService;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PaperlessSyncService syncService;

    private long paperlessDocumentId = 600000;

    @BeforeEach
    void setUp() {
        receiptRepository.deleteAll();
        receiptRepository.flush();
        when(syncService.currentStatus()).thenReturn(new SyncStatusDto(
                OffsetDateTime.of(2026, 6, 3, 12, 0, 0, 0, ZoneOffset.UTC),
                SyncStatus.SUCCESS,
                7,
                0,
                0,
                false));
    }

    // Verifies item search filters, highlighting, and safe sorting defaults for the main search UI.
    @Test
    void searchSupportsFiltersHighlightsAndSafeFallbackSorting() {
        Category lebensmittel = category("Lebensmittel");
        Category getraenke = category("Getraenke");
        Receipt milkReceipt = receipt("REWE", LocalDate.now(), null, null, null,
                item(0, "Bio Milch", "2.49", lebensmittel),
                item(1, "Cola", "1.99", getraenke));
        receipt("DM", LocalDate.now(), null, null, null,
                item(0, "Shampoo", "4.99", category("Koerperpflege")));

        PageResponse<SearchResultDto> result = queryApiService.search(
                "milch",
                "rewe",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                List.of(lebensmittel.getId()),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                -5,
                500,
                "unknown",
                "DOWN");

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.sortBy()).isEqualTo("receipt.receiptDate");
        assertThat(result.sortDir()).isEqualTo("desc");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement().satisfies(item -> {
            assertThat(item.receiptId()).isEqualTo(milkReceipt.getId());
            assertThat(item.storeName()).isEqualTo("REWE");
            assertThat(item.description()).isEqualTo("Bio Milch");
            assertThat(item.categoryName()).isEqualTo("Lebensmittel");
            assertThat(item.highlights()).containsExactly("milch");
        });
    }

    // Verifies that blank searches still return stable results and do not invent highlight markers.
    @Test
    void searchWithoutQueryLeavesHighlightsEmptyAndCanSortByTotalPriceAscending() {
        receipt("REWE", LocalDate.now(), null, null, null,
                item(0, "Brot", "1.00", category("Brot und Backwaren")),
                item(1, "Milch", "2.00", category("Lebensmittel")));
        receipt("REWE", LocalDate.now(), null, null, null,
                item(0, "Apfel", "0.50", category("Salat, Obst & Gemüse")));

        PageResponse<SearchResultDto> result = queryApiService.search(
                "",
                "rewe",
                null,
                null,
                List.of(),
                null,
                null,
                0,
                10,
                "totalPrice",
                "asc");

        assertThat(result.sortBy()).isEqualTo("totalPrice");
        assertThat(result.sortDir()).isEqualTo("asc");
        assertThat(result.content()).first()
                .satisfies(item -> assertThat(item.highlights()).isEmpty());
    }

    // Verifies report grouping keeps uncategorized items visible as "Ohne Kategorie" instead of dropping them.
    @Test
    void reportByCategoryAggregatesUncategorizedItemsAndSortsDescending() {
        Category lebensmittel = category("Lebensmittel");
        Category koerperpflege = category("Koerperpflege");
        receipt("REWE", LocalDate.now(), null, null, null,
                item(0, "Milch", "2.00", lebensmittel),
                item(1, "Ohne Kategorie 1", "5.00", null));
        receipt("DM", LocalDate.now(), null, null, null,
                item(0, "Shampoo", "3.00", koerperpflege));

        List<ReportDto.ByCategory> report = queryApiService.reportByCategory(null, null, List.of(), null);

        assertThat(report).extracting(ReportDto.ByCategory::categoryName)
                .containsExactly("Ohne Kategorie", "Koerperpflege", "Lebensmittel");
        assertThat(report).extracting(ReportDto.ByCategory::total)
                .containsExactly(new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("2.00"));
    }

    // Verifies period reports support UI-selected groupings and fall back safely for invalid group names.
    @Test
    void reportByPeriodSupportsInvalidDefaultMonthAndExplicitYearOrWeekGrouping() {
        Category lebensmittel = category("Lebensmittel");
        LocalDate currentMonthDate = LocalDate.now().withDayOfMonth(3);
        LocalDate previousMonthDate = currentMonthDate.minusMonths(1);
        receipt("REWE", currentMonthDate, null, null, null,
                item(0, "Milch", "2.00", lebensmittel));
        receipt("REWE", previousMonthDate, null, null, null,
                item(0, "Brot", "3.00", lebensmittel));

        List<ReportDto.ByPeriod> defaultMonth = queryApiService.reportByPeriod(null, null, List.of(), null, "invalid");
        List<ReportDto.ByPeriod> year = queryApiService.reportByPeriod(null, null, List.of(), null, "year");
        List<ReportDto.ByPeriod> week = queryApiService.reportByPeriod(null, null, List.of(), null, "week");

        assertThat(defaultMonth).extracting(ReportDto.ByPeriod::period)
                .containsExactly(
                        previousMonthDate.getYear() + "-" + "%02d".formatted(previousMonthDate.getMonthValue()),
                        currentMonthDate.getYear() + "-" + "%02d".formatted(currentMonthDate.getMonthValue()));
        assertThat(year).singleElement().satisfies(row -> {
            assertThat(row.period()).isEqualTo(Integer.toString(currentMonthDate.getYear()));
            assertThat(row.total()).isEqualByComparingTo("5.00");
        });
        assertThat(week).extracting(ReportDto.ByPeriod::periodStart, ReportDto.ByPeriod::period, ReportDto.ByPeriod::total)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                previousMonthDate.with(java.time.temporal.ChronoField.DAY_OF_WEEK, 1),
                                previousMonthDate.with(java.time.temporal.ChronoField.DAY_OF_WEEK, 1).getYear()
                                        + "-W" + "%02d".formatted(previousMonthDate.with(java.time.temporal.ChronoField.DAY_OF_WEEK, 1)
                                        .get(java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR)),
                                new BigDecimal("3.00")),
                        org.assertj.core.groups.Tuple.tuple(
                                currentMonthDate.with(java.time.temporal.ChronoField.DAY_OF_WEEK, 1),
                                currentMonthDate.with(java.time.temporal.ChronoField.DAY_OF_WEEK, 1).getYear()
                                        + "-W" + "%02d".formatted(currentMonthDate.with(java.time.temporal.ChronoField.DAY_OF_WEEK, 1)
                                        .get(java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR)),
                                new BigDecimal("2.00")));
    }

    // Verifies store reports keep receipts with missing store names visible under an explicit fallback label.
    @Test
    void reportByStoreFallsBackToUnknownForMissingStoreNames() {
        Category lebensmittel = category("Lebensmittel");
        receipt(null, LocalDate.now(), null, null, null,
                item(0, "Milch", "1.00", lebensmittel));
        receipt("   ", LocalDate.now(), null, null, null,
                item(0, "Brot", "2.00", lebensmittel));
        receipt("REWE", LocalDate.now(), null, null, null,
                item(0, "Käse", "4.00", lebensmittel));

        List<ReportDto.ByStore> report = queryApiService.reportByStore(null, null, List.of(), null);

        assertThat(report).extracting(ReportDto.ByStore::storeName)
                .containsExactly("REWE", "Unbekannt");
        assertThat(report).last()
                .satisfies(row -> {
                    assertThat(row.total()).isEqualByComparingTo("3.00");
                    assertThat(row.receiptCount()).isEqualTo(2);
                });
    }

    // Verifies top-item reports clamp unsafe limits and keep blank OCR descriptions visible under a fallback label.
    @Test
    void topItemsFallsBackForBlankDescriptionsAndClampsLimit() {
        receipt("REWE", LocalDate.now(), null, null, null,
                item(0, "   ", "5.00", null),
                item(1, "Apfel", "4.00", null),
                item(2, "Banane", "1.00", null));

        List<ReportDto.TopItem> report = queryApiService.topItems(null, null, List.of(), null, 0);

        assertThat(report).hasSize(1);
        assertThat(report).first().satisfies(row -> {
            assertThat(row.description()).isEqualTo("Unbekannte Position");
            assertThat(row.total()).isEqualByComparingTo("5.00");
            assertThat(row.count()).isEqualTo(1);
        });
    }

    // Verifies bonus reports aggregate only newly earned values and ignore receipts without a real bonus type.
    @Test
    void bonusReportAggregatesBonusValuesAndIgnoresBlankBonusTypes() {
        receipt("REWE", LocalDate.now(), new BigDecimal("2.50"), new BigDecimal("5.00"), "REWE Bonus",
                item(0, "Milch", "2.50", category("Lebensmittel")));
        receipt("REWE", LocalDate.now(), new BigDecimal("1.25"), new BigDecimal("1.50"), "REWE Bonus",
                item(0, "Brot", "1.25", category("Brot und Backwaren")));
        receipt("REWE", LocalDate.now(), new BigDecimal("9.99"), new BigDecimal("9.00"), " ",
                item(0, "Ignoriert", "9.99", category("Lebensmittel")));

        List<ReportDto.Bonus> report = queryApiService.bonusReport(null, null, null);

        assertThat(report).singleElement().satisfies(row -> {
            assertThat(row.bonusType()).isEqualTo("REWE Bonus");
            assertThat(row.totalPoints()).isEqualByComparingTo("6.50");
            assertThat(row.totalEarnedBalance()).isEqualByComparingTo("3.75");
        });
    }

    // Verifies dashboard totals, uncategorized counts, recent receipts, and sync status for the first UI screen.
    @Test
    void dashboardUsesCurrentAndPreviousMonthTotalsAndUncategorizedCount() {
        Category lebensmittel = category("Lebensmittel");
        Category getraenke = category("Getraenke");
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(Math.min(2, LocalDate.now().getDayOfMonth()));
        LocalDate previousMonth = currentMonth.minusMonths(1);
        receipt("REWE", currentMonth, null, null, null,
                item(0, "Milch", "4.00", lebensmittel),
                item(1, "Ohne Kategorie", "2.00", null));
        receipt("DM", previousMonth, null, null, null,
                item(0, "Cola", "3.00", getraenke));

        DashboardDto dashboard = queryApiService.dashboard();

        assertThat(dashboard.currentMonthTotal()).isEqualByComparingTo("6.00");
        assertThat(dashboard.previousMonthTotal()).isEqualByComparingTo("3.00");
        assertThat(dashboard.uncategorizedItemsCount()).isEqualTo(1);
        assertThat(dashboard.lastSyncStatus().lastSyncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(dashboard.lastSyncStatus().isSyncing()).isFalse();
        assertThat(dashboard.recentReceipts()).hasSize(2);
        assertThat(dashboard.recentReceipts().get(0).storeName()).isEqualTo("DM");
    }

    private Receipt receipt(
            String storeName,
            LocalDate date,
            BigDecimal bonusBalance,
            BigDecimal bonusPoints,
            String bonusType,
            ItemSpec... items) {
        Receipt receipt = new Receipt((int) paperlessDocumentId++, "raw text");
        receipt.applyParseResult(
                ParseStatus.PARSED,
                null,
                date,
                LocalTime.of(12, 0),
                storeName,
                null,
                total(items),
                "EUR",
                bonusBalance,
                bonusPoints,
                bonusType);
        for (int index = 0; index < items.length; index++) {
            ReceiptItem item = new ReceiptItem(index, items[index].description(), items[index].total());
            if (items[index].category() != null) {
                item.assignCategory(items[index].category(), CategorySource.RULE);
            }
            receipt.addItem(item);
        }
        return receiptRepository.saveAndFlush(receipt);
    }

    private Category category(String name) {
        return categoryRepository.findByName(name).orElseThrow();
    }

    private ItemSpec item(int position, String description, String total, Category category) {
        return new ItemSpec(position, description, new BigDecimal(total), category);
    }

    private BigDecimal total(ItemSpec... items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemSpec item : items) {
            total = total.add(item.total());
        }
        return total;
    }

    private record ItemSpec(int positionIndex, String description, BigDecimal total, Category category) {
    }

    @TestConfiguration
    static class FakeSyncServiceConfig {

        @Bean
        @Primary
        PaperlessSyncService paperlessSyncService() {
            return mock(PaperlessSyncService.class);
        }
    }
}
