package de.ebon.api;

import de.ebon.api.dto.ReportDto;
import de.ebon.api.service.QueryApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportsController {

    private final QueryApiService queryApiService;

    public ReportsController(QueryApiService queryApiService) {
        this.queryApiService = queryApiService;
    }

    @GetMapping("/api/reports/by-category")
    @Operation(summary = "Ausgaben nach Kategorie")
    public List<ReportDto.ByCategory> byCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long productVariantId) {
        if (hasProductFilter(productFamilyId, productVariantId)) {
            return queryApiService.reportByCategory(
                    dateFrom, dateTo, parseIds(categoryIds), store, productFamilyId, productVariantId);
        }
        return queryApiService.reportByCategory(dateFrom, dateTo, parseIds(categoryIds), store);
    }

    @GetMapping("/api/reports/by-period")
    @Operation(summary = "Ausgaben nach Zeitraum")
    public List<ReportDto.ByPeriod> byPeriod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "month") String groupBy,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long productVariantId) {
        if (hasProductFilter(productFamilyId, productVariantId)) {
            return queryApiService.reportByPeriod(
                    dateFrom, dateTo, parseIds(categoryIds), store, groupBy, productFamilyId, productVariantId);
        }
        return queryApiService.reportByPeriod(dateFrom, dateTo, parseIds(categoryIds), store, groupBy);
    }

    @GetMapping("/api/reports/by-store")
    @Operation(summary = "Ausgaben nach Geschaeft")
    public List<ReportDto.ByStore> byStore(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long productVariantId) {
        if (hasProductFilter(productFamilyId, productVariantId)) {
            return queryApiService.reportByStore(
                    dateFrom, dateTo, parseIds(categoryIds), store, productFamilyId, productVariantId);
        }
        return queryApiService.reportByStore(dateFrom, dateTo, parseIds(categoryIds), store);
    }

    @GetMapping("/api/reports/top-items")
    @Operation(summary = "Haeufigste oder teuerste Positionen")
    public List<ReportDto.TopItem> topItems(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long productVariantId) {
        if (hasProductFilter(productFamilyId, productVariantId)) {
            return queryApiService.topItems(
                    dateFrom, dateTo, parseIds(categoryIds), store, size, productFamilyId, productVariantId);
        }
        return queryApiService.topItems(dateFrom, dateTo, parseIds(categoryIds), store, size);
    }

    @GetMapping("/api/reports/top-products")
    @Operation(summary = "Haeufig oder teuer gekaufte Produktfamilien")
    public List<ReportDto.TopProduct> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "total") String sortBy) {
        return queryApiService.topProducts(dateFrom, dateTo, store, size, sortBy);
    }

    @GetMapping("/api/reports/top-products/export")
    @Operation(summary = "Top-Produktfamilien als CSV")
    public ResponseEntity<String> topProductsExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "total") String sortBy) {
        StringBuilder csv = new StringBuilder("productFamilyId,productFamilyName,total,count\n");
        topProducts(dateFrom, dateTo, store, size, sortBy)
                .forEach(row -> csv.append(row.productFamilyId()).append(',')
                        .append(csv(row.productFamilyName())).append(',')
                        .append(row.total()).append(',')
                        .append(row.count()).append('\n'));
        return csv("report-top-products.csv", csv.toString());
    }

    @GetMapping("/api/reports/bonus")
    @Operation(summary = "Neu gesammelte Bonuspunkte und Bonusguthaben")
    public List<ReportDto.Bonus> bonus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String store) {
        return queryApiService.bonusReport(dateFrom, dateTo, store);
    }

    @GetMapping("/api/reports/by-category/export")
    @Operation(summary = "Kategorie-Report als CSV")
    public ResponseEntity<String> byCategoryExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long productVariantId) {
        StringBuilder csv = new StringBuilder("categoryId,categoryName,total\n");
        byCategory(dateFrom, dateTo, categoryIds, store, productFamilyId, productVariantId)
                .forEach(row -> csv.append(row.categoryId()).append(',')
                        .append(csv(row.categoryName())).append(',')
                        .append(row.total()).append('\n'));
        return csv("report-by-category.csv", csv.toString());
    }

    @GetMapping("/api/reports/by-period/export")
    @Operation(summary = "Zeitraum-Report als CSV")
    public ResponseEntity<String> byPeriodExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "month") String groupBy,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long productVariantId) {
        StringBuilder csv = new StringBuilder("periodStart,period,total\n");
        byPeriod(dateFrom, dateTo, categoryIds, store, groupBy, productFamilyId, productVariantId)
                .forEach(row -> csv.append(row.periodStart()).append(',')
                        .append(csv(row.period())).append(',')
                        .append(row.total()).append('\n'));
        return csv("report-by-period.csv", csv.toString());
    }

    @GetMapping("/api/reports/by-store/export")
    @Operation(summary = "Geschaeft-Report als CSV")
    public ResponseEntity<String> byStoreExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long productVariantId) {
        StringBuilder csv = new StringBuilder("storeName,total,receiptCount\n");
        byStore(dateFrom, dateTo, categoryIds, store, productFamilyId, productVariantId)
                .forEach(row -> csv.append(csv(row.storeName())).append(',')
                        .append(row.total()).append(',')
                        .append(row.receiptCount()).append('\n'));
        return csv("report-by-store.csv", csv.toString());
    }

    @GetMapping("/api/reports/top-items/export")
    @Operation(summary = "Top-Artikel-Report als CSV")
    public ResponseEntity<String> topItemsExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long productFamilyId,
            @RequestParam(required = false) Long productVariantId) {
        StringBuilder csv = new StringBuilder("description,total,count\n");
        topItems(dateFrom, dateTo, categoryIds, store, size, productFamilyId, productVariantId)
                .forEach(row -> csv.append(csv(row.description())).append(',')
                        .append(row.total()).append(',')
                        .append(row.count()).append('\n'));
        return csv("report-top-items.csv", csv.toString());
    }

    @GetMapping("/api/reports/bonus/export")
    @Operation(summary = "Bonus-Report als CSV")
    public ResponseEntity<String> bonusExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String store) {
        StringBuilder csv = new StringBuilder("bonusType,totalPoints,totalEarnedBalance\n");
        bonus(dateFrom, dateTo, store)
                .forEach(row -> csv.append(csv(row.bonusType())).append(',')
                        .append(row.totalPoints()).append(',')
                        .append(row.totalEarnedBalance()).append('\n'));
        return csv("report-bonus.csv", csv.toString());
    }

    private ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private boolean hasProductFilter(Long productFamilyId, Long productVariantId) {
        return productFamilyId != null || productVariantId != null;
    }

    private List<Long> parseIds(String categoryIds) {
        if (categoryIds == null || categoryIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(categoryIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .toList();
    }
}
