package de.ebon.api.service;

import de.ebon.api.dto.DashboardDto;
import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ReceiptDto;
import de.ebon.api.dto.ReportDto;
import de.ebon.api.dto.SearchResultDto;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.sync.PaperlessSyncService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryApiService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReceiptRepository receiptRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final ReceiptApiService receiptApiService;
    private final PaperlessSyncService syncService;

    public QueryApiService(
            ReceiptRepository receiptRepository,
            ReceiptItemRepository receiptItemRepository,
            ReceiptApiService receiptApiService,
            PaperlessSyncService syncService) {
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.receiptApiService = receiptApiService;
        this.syncService = syncService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SearchResultDto> search(
            String q,
            String store,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<Long> categoryIds,
            BigDecimal amountMin,
            BigDecimal amountMax,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        String safeSortBy = safeItemSort(sortBy);
        String safeSortDir = safeSortDirection(sortDir);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.fromString(safeSortDir), safeSortBy));
        return PageResponse.from(receiptItemRepository.findAll(
                itemSpecification(q, store, dateFrom, dateTo, categoryIds, amountMin, amountMax),
                pageable).map(item -> toSearchResult(item, q)), safeSortBy, safeSortDir);
    }

    @Transactional(readOnly = true)
    public List<ReportDto.ByCategory> reportByCategory(
            LocalDate dateFrom,
            LocalDate dateTo,
            List<Long> categoryIds,
            String store) {
        Map<CategoryKey, BigDecimal> totals = filteredItems(dateFrom, dateTo, categoryIds, store).stream()
                .collect(Collectors.groupingBy(
                        item -> new CategoryKey(
                                item.getCategory() == null ? null : item.getCategory().getId(),
                                item.getCategory() == null ? "Ohne Kategorie" : item.getCategory().getName()),
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, this::totalPrice, BigDecimal::add)));
        return totals.entrySet().stream()
                .map(entry -> new ReportDto.ByCategory(entry.getKey().id(), entry.getKey().name(), entry.getValue()))
                .sorted(Comparator.comparing(ReportDto.ByCategory::total).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto.ByPeriod> reportByPeriod(
            LocalDate dateFrom,
            LocalDate dateTo,
            List<Long> categoryIds,
            String store,
            String groupBy) {
        Map<LocalDate, BigDecimal> totals = filteredItems(dateFrom, dateTo, categoryIds, store).stream()
                .filter(item -> item.getReceipt().getReceiptDate() != null)
                .collect(Collectors.groupingBy(
                        item -> periodStart(item.getReceipt().getReceiptDate(), groupBy),
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, this::totalPrice, BigDecimal::add)));
        return totals.entrySet().stream()
                .map(entry -> new ReportDto.ByPeriod(entry.getKey(), periodLabel(entry.getKey(), groupBy), entry.getValue()))
                .sorted(Comparator.comparing(ReportDto.ByPeriod::periodStart))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto.ByStore> reportByStore(
            LocalDate dateFrom,
            LocalDate dateTo,
            List<Long> categoryIds,
            String store) {
        Map<String, List<ReceiptItem>> byStore = filteredItems(dateFrom, dateTo, categoryIds, store).stream()
                .collect(Collectors.groupingBy(
                        item -> blankToFallback(item.getReceipt().getStoreName(), "Unbekannt"),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return byStore.entrySet().stream()
                .map(entry -> new ReportDto.ByStore(
                        entry.getKey(),
                        entry.getValue().stream().map(this::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().stream().map(item -> item.getReceipt().getId()).distinct().count()))
                .sorted(Comparator.comparing(ReportDto.ByStore::total).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto.TopItem> topItems(
            LocalDate dateFrom,
            LocalDate dateTo,
            List<Long> categoryIds,
            String store,
            int limit) {
        Map<String, List<ReceiptItem>> byDescription = filteredItems(dateFrom, dateTo, categoryIds, store).stream()
                .collect(Collectors.groupingBy(
                        item -> blankToFallback(item.getDescription(), "Unbekannte Position"),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return byDescription.entrySet().stream()
                .map(entry -> new ReportDto.TopItem(
                        entry.getKey(),
                        entry.getValue().stream().map(this::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().size()))
                .sorted(Comparator.comparing(ReportDto.TopItem::total).reversed())
                .limit(Math.min(Math.max(limit, 1), 100))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto.Bonus> bonusReport(LocalDate dateFrom, LocalDate dateTo, String store) {
        Map<String, List<Receipt>> byType = receiptRepository.findAll(receiptSpecification(dateFrom, dateTo, store)).stream()
                .filter(receipt -> receipt.getBonusType() != null && !receipt.getBonusType().isBlank())
                .collect(Collectors.groupingBy(
                        Receipt::getBonusType,
                        LinkedHashMap::new,
                        Collectors.toList()));
        return byType.entrySet().stream()
                .map(entry -> new ReportDto.Bonus(
                        entry.getKey(),
                        entry.getValue().stream().map(Receipt::getBonusPoints).filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().stream().map(Receipt::getBonusBalance).filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        LocalDate previousMonthStart = currentMonthStart.minusMonths(1);
        LocalDate previousMonthEnd = currentMonthStart.minusDays(1);
        BigDecimal currentMonthTotal = filteredItems(currentMonthStart, today, List.of(), null).stream()
                .map(this::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousMonthTotal = filteredItems(previousMonthStart, previousMonthEnd, List.of(), null).stream()
                .map(this::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ReceiptDto> recentReceipts = receiptRepository.findAll(
                        receiptSpecification(null, null, null),
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "importedAt")))
                .map(receipt -> receiptApiService.toReceiptDto(receipt, List.of()))
                .getContent();
        return new DashboardDto(
                currentMonthTotal,
                previousMonthTotal,
                reportByCategory(currentMonthStart, today, List.of(), null),
                bonusReport(null, null, null),
                recentReceipts,
                receiptItemRepository.countByCategoryIsNullAndReceipt_DeletedAtIsNull(),
                syncService.currentStatus());
    }

    private List<ReceiptItem> filteredItems(
            LocalDate dateFrom,
            LocalDate dateTo,
            List<Long> categoryIds,
            String store) {
        return receiptItemRepository.findAll(itemSpecification(null, store, dateFrom, dateTo, categoryIds, null, null));
    }

    private Specification<Receipt> receiptSpecification(LocalDate dateFrom, LocalDate dateTo, String store) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (dateFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("receiptDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("receiptDate"), dateTo));
            }
            if (store != null && !store.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("storeName")),
                        "%" + store.toLowerCase(Locale.ROOT).trim() + "%"));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<ReceiptItem> itemSpecification(
            String q,
            String store,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<Long> categoryIds,
            BigDecimal amountMin,
            BigDecimal amountMax) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("receipt").get("deletedAt")));
            if (q != null && !q.isBlank()) {
                String value = "%" + q.toLowerCase(Locale.ROOT).trim() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("description")), value),
                        builder.like(builder.lower(root.get("receipt").get("storeName")), value)));
            }
            if (store != null && !store.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("receipt").get("storeName")),
                        "%" + store.toLowerCase(Locale.ROOT).trim() + "%"));
            }
            if (dateFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("receipt").get("receiptDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("receipt").get("receiptDate"), dateTo));
            }
            if (categoryIds != null && !categoryIds.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categoryIds));
            }
            if (amountMin != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("totalPrice"), amountMin));
            }
            if (amountMax != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("totalPrice"), amountMax));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SearchResultDto toSearchResult(ReceiptItem item, String query) {
        return new SearchResultDto(
                item.getReceipt().getId(),
                item.getId(),
                item.getReceipt().getReceiptDate(),
                item.getReceipt().getStoreName(),
                item.getDescription(),
                item.getTotalPrice(),
                item.getCategory() == null ? null : item.getCategory().getId(),
                item.getCategory() == null ? null : item.getCategory().getName(),
                query == null || query.isBlank() ? List.of() : List.of(query));
    }

    private BigDecimal totalPrice(ReceiptItem item) {
        return item.getTotalPrice() == null ? BigDecimal.ZERO : item.getTotalPrice();
    }

    private LocalDate periodStart(LocalDate date, String groupBy) {
        return switch (safeGroupBy(groupBy)) {
            case "year" -> date.withDayOfYear(1);
            case "month" -> date.withDayOfMonth(1);
            case "week" -> date.with(ChronoField.DAY_OF_WEEK, 1);
            default -> date;
        };
    }

    private String periodLabel(LocalDate periodStart, String groupBy) {
        return switch (safeGroupBy(groupBy)) {
            case "year" -> Integer.toString(periodStart.getYear());
            case "month" -> periodStart.getYear() + "-" + "%02d".formatted(periodStart.getMonthValue());
            case "week" -> periodStart.getYear() + "-W" + "%02d".formatted(periodStart.get(ChronoField.ALIGNED_WEEK_OF_YEAR));
            default -> periodStart.toString();
        };
    }

    private String safeGroupBy(String groupBy) {
        Set<String> allowed = Set.of("day", "week", "month", "year");
        return groupBy == null || !allowed.contains(groupBy) ? "month" : groupBy;
    }

    private String safeItemSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "receipt.receiptDate";
        }
        return switch (sortBy) {
            case "receiptDate" -> "receipt.receiptDate";
            case "storeName" -> "receipt.storeName";
            case "description", "totalPrice" -> sortBy;
            default -> "receipt.receiptDate";
        };
    }

    private String safeSortDirection(String sortDir) {
        return "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record CategoryKey(Long id, String name) {
    }
}
