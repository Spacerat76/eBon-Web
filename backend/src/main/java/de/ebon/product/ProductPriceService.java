package de.ebon.product;

import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ProductPriceObservationDto;
import de.ebon.api.dto.ProductPriceReportDto;
import de.ebon.api.dto.ProductPriceStatisticsDto;
import de.ebon.api.dto.ProductPriceStoreDto;
import de.ebon.api.dto.ProductPriceTrendPointDto;
import de.ebon.api.dto.ProductPriceVariantSummaryDto;
import de.ebon.persistence.model.ProductAssignmentLog;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds product-price comparisons from persisted receipt items. Only confirmed or automatically
 * assigned, non-excluded positions contribute to comparison figures.
 */
@Service
public class ProductPriceService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal OUTLIER_HIGH_FACTOR = new BigDecimal("3");
    private static final BigDecimal OUTLIER_LOW_FACTOR = new BigDecimal("0.3333333333");

    private final ReceiptItemRepository receiptItemRepository;
    private final ProductFamilyRepository productFamilyRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductAssignmentLogRepository assignmentLogRepository;

    public ProductPriceService(
            ReceiptItemRepository receiptItemRepository,
            ProductFamilyRepository productFamilyRepository,
            ProductVariantRepository productVariantRepository,
            ProductAssignmentLogRepository assignmentLogRepository) {
        this.receiptItemRepository = receiptItemRepository;
        this.productFamilyRepository = productFamilyRepository;
        this.productVariantRepository = productVariantRepository;
        this.assignmentLogRepository = assignmentLogRepository;
    }

    @Transactional(readOnly = true)
    public ProductPriceReportDto familyReport(Long productFamilyId, ProductPriceFilter filter) {
        ProductFamily family = requireFamily(productFamilyId);
        return report("FAMILY", family, null, receiptItemRepository.findByProductFamily_Id(productFamilyId), filter, true);
    }

    @Transactional(readOnly = true)
    public ProductPriceReportDto variantReport(Long productVariantId, ProductPriceFilter filter) {
        ProductVariant variant = requireVariant(productVariantId);
        return report(
                "VARIANT",
                variant.getProductFamily(),
                variant,
                receiptItemRepository.findByProductVariant_Id(productVariantId),
                filter,
                false);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductPriceObservationDto> familyObservations(
            Long productFamilyId, ProductPriceFilter filter, int page, int size) {
        requireFamily(productFamilyId);
        return observations(receiptItemRepository.findByProductFamily_Id(productFamilyId), filter, page, size, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductPriceObservationDto> variantObservations(
            Long productVariantId, ProductPriceFilter filter, int page, int size) {
        requireVariant(productVariantId);
        return observations(receiptItemRepository.findByProductVariant_Id(productVariantId), filter, page, size, false);
    }

    @Transactional
    public ProductPriceObservationDto exclude(Long receiptItemId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Ein Ausschluss braucht eine Begründung.");
        }
        ReceiptItem item = eligibleItem(receiptItemId);
        item.setProductPriceExclusion(true, reason);
        audit(item, "PRICE_EXCLUDED: " + reason.trim());
        return observation(item, false, false);
    }

    @Transactional
    public ProductPriceObservationDto include(Long receiptItemId) {
        ReceiptItem item = eligibleItem(receiptItemId);
        item.setProductPriceExclusion(false, null);
        audit(item, "PRICE_INCLUDED");
        return observation(item, true, false);
    }

    @Transactional(readOnly = true)
    public String familyCsv(Long productFamilyId, ProductPriceFilter filter) {
        requireFamily(productFamilyId);
        return csv(receiptItemRepository.findByProductFamily_Id(productFamilyId), filter, true);
    }

    @Transactional(readOnly = true)
    public String variantCsv(Long productVariantId, ProductPriceFilter filter) {
        requireVariant(productVariantId);
        return csv(receiptItemRepository.findByProductVariant_Id(productVariantId), filter, false);
    }

    private ProductPriceReportDto report(
            String scope,
            ProductFamily family,
            ProductVariant variant,
            List<ReceiptItem> items,
            ProductPriceFilter requestedFilter,
            boolean useNormalizedUnitPrice) {
        ProductPriceFilter filter = safeFilter(requestedFilter);
        List<PricedItem> all = filtered(items, filter, useNormalizedUnitPrice);
        List<PricedItem> included = all.stream().filter(PricedItem::includedInComparison).toList();
        Set<ReceiptItem> outliers = findOutliers(included);

        return new ProductPriceReportDto(
                scope,
                family.getId(),
                family.getName(),
                variant == null ? null : variant.getId(),
                variant == null ? null : variant.getName(),
                useNormalizedUnitPrice ? "NORMALIZED_UNIT_PRICE" : "EFFECTIVE_PRICE",
                statistics(included),
                storeSummaries(included, filter),
                trend(included, outliers),
                useNormalizedUnitPrice ? variantSummaries(included) : List.of());
    }

    private PageResponse<ProductPriceObservationDto> observations(
            List<ReceiptItem> items,
            ProductPriceFilter requestedFilter,
            int requestedPage,
            int requestedSize,
            boolean useNormalizedUnitPrice) {
        List<PricedItem> all = filtered(items, safeFilter(requestedFilter), useNormalizedUnitPrice);
        Set<ReceiptItem> outliers = findOutliers(all.stream().filter(PricedItem::includedInComparison).toList());
        List<ProductPriceObservationDto> content = all.stream()
                .sorted(Comparator.comparing((PricedItem priced) -> receiptDate(priced.item()),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(priced -> priced.item().getId(), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(priced -> observation(priced.item(), priced.includedInComparison(), outliers.contains(priced.item())))
                .toList();
        return page(content, requestedPage, requestedSize);
    }

    private List<PricedItem> filtered(List<ReceiptItem> items, ProductPriceFilter filter, boolean useNormalizedUnitPrice) {
        return items.stream()
                .filter(this::isActiveReceipt)
                .filter(item -> matchesFilter(item, filter))
                .filter(item -> filter.includeExcluded() || !item.isExcludedFromProductPriceComparison())
                .map(item -> priced(item, useNormalizedUnitPrice))
                .toList();
    }

    private ProductPriceFilter safeFilter(ProductPriceFilter filter) {
        return filter == null ? new ProductPriceFilter(null, null, null, null, true) : filter;
    }

    private boolean isActiveReceipt(ReceiptItem item) {
        return item != null && item.getReceipt() != null && item.getReceipt().getDeletedAt() == null;
    }

    private boolean matchesFilter(ReceiptItem item, ProductPriceFilter filter) {
        LocalDate date = receiptDate(item);
        if (filter.dateFrom() != null && (date == null || date.isBefore(filter.dateFrom()))) {
            return false;
        }
        if (filter.dateTo() != null && (date == null || date.isAfter(filter.dateTo()))) {
            return false;
        }
        return filter.store() == null || filter.store().isBlank()
                || storeName(item).toLowerCase(Locale.ROOT).contains(filter.store().trim().toLowerCase(Locale.ROOT));
    }

    private PricedItem priced(ReceiptItem item, boolean useNormalizedUnitPrice) {
        ProductPriceCalculator.PriceQuote quote = ProductPriceCalculator.quote(item);
        BigDecimal comparisonPrice = useNormalizedUnitPrice ? quote.normalizedUnitPrice() : quote.effectivePrice();
        String comparisonUnit = useNormalizedUnitPrice ? quote.normalizedUnit() : "EUR";
        boolean included = isEligible(item) && !item.isExcludedFromProductPriceComparison() && comparisonPrice != null;
        return new PricedItem(item, quote, comparisonPrice, comparisonUnit, included);
    }

    private boolean isEligible(ReceiptItem item) {
        return item.getProductFamily() != null
                && (item.getProductAssignmentStatus() == ProductAssignmentStatus.CONFIRMED
                || item.getProductAssignmentStatus() == ProductAssignmentStatus.AUTO_ASSIGNED);
    }

    private List<ProductPriceStatisticsDto> statistics(List<PricedItem> included) {
        return included.stream()
                .filter(priced -> priced.comparisonPrice() != null && priced.comparisonUnit() != null)
                .collect(Collectors.groupingBy(PricedItem::comparisonUnit))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> statistics(entry.getKey(), entry.getValue()))
                .toList();
    }

    private ProductPriceStatisticsDto statistics(String unit, List<PricedItem> values) {
        List<PricedItem> sortedByDate = values.stream()
                .sorted(Comparator.comparing((PricedItem priced) -> receiptDate(priced.item()),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(priced -> priced.item().getId(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<BigDecimal> prices = values.stream().map(PricedItem::comparisonPrice).sorted().toList();
        BigDecimal total = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        PricedItem latest = sortedByDate.getFirst();
        return new ProductPriceStatisticsDto(
                unit,
                latest.comparisonPrice(),
                receiptDate(latest.item()),
                prices.getFirst(),
                total.divide(BigDecimal.valueOf(prices.size()), 4, RoundingMode.HALF_UP),
                median(prices),
                prices.size());
    }

    private BigDecimal median(List<BigDecimal> sortedPrices) {
        int middle = sortedPrices.size() / 2;
        if (sortedPrices.size() % 2 == 1) {
            return sortedPrices.get(middle);
        }
        return sortedPrices.get(middle - 1).add(sortedPrices.get(middle)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private List<ProductPriceStoreDto> storeSummaries(List<PricedItem> included, ProductPriceFilter filter) {
        return included.stream()
                .filter(priced -> priced.comparisonPrice() != null && priced.comparisonUnit() != null)
                .collect(Collectors.groupingBy(priced -> storeKey(priced.item(), priced.comparisonUnit(), filter)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(StoreKey::label).thenComparing(StoreKey::unit)))
                .map(entry -> {
                    ProductPriceStatisticsDto statistics = statistics(entry.getKey().unit(), entry.getValue());
                    StoreKey key = entry.getKey();
                    return new ProductPriceStoreDto(
                            key.storeName(),
                            key.storeBranch(),
                            key.label(),
                            statistics.priceUnit(),
                            statistics.latestPrice(),
                            statistics.latestReceiptDate(),
                            statistics.minimumPrice(),
                            statistics.averagePrice(),
                            statistics.medianPrice(),
                            statistics.observationCount());
                })
                .toList();
    }

    private StoreKey storeKey(ReceiptItem item, String unit, ProductPriceFilter filter) {
        String storeName = storeName(item);
        String branch = filter.grouping() == de.ebon.api.dto.ProductPriceGrouping.STORE_BRANCH ? storeBranch(item) : null;
        String label = branch == null || branch.isBlank() ? storeName : storeName + " - " + branch;
        return new StoreKey(storeName, branch, label, unit);
    }

    private List<ProductPriceTrendPointDto> trend(List<PricedItem> included, Set<ReceiptItem> outliers) {
        return included.stream()
                .filter(priced -> priced.comparisonPrice() != null && priced.comparisonUnit() != null)
                .sorted(Comparator.comparing((PricedItem priced) -> receiptDate(priced.item()), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(priced -> priced.item().getId(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(priced -> new ProductPriceTrendPointDto(
                        priced.item().getId(),
                        receiptDate(priced.item()),
                        storeName(priced.item()),
                        priced.comparisonPrice(),
                        priced.comparisonUnit(),
                        outliers.contains(priced.item())))
                .toList();
    }

    private List<ProductPriceVariantSummaryDto> variantSummaries(List<PricedItem> included) {
        return included.stream()
                .filter(priced -> priced.item().getProductVariant() != null)
                .collect(Collectors.groupingBy(priced -> priced.item().getProductVariant()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ProductVariant::getName)))
                .map(entry -> {
                    ProductVariant variant = entry.getKey();
                    List<PricedItem> values = entry.getValue();
                    List<PricedItem> sorted = values.stream()
                            .sorted(Comparator.comparing((PricedItem priced) -> receiptDate(priced.item()),
                                            Comparator.nullsLast(Comparator.reverseOrder()))
                                    .thenComparing(priced -> priced.item().getId(), Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                    List<BigDecimal> effectivePrices = values.stream()
                            .map(priced -> priced.quote().effectivePrice())
                            .filter(java.util.Objects::nonNull)
                            .sorted()
                            .toList();
                    return new ProductPriceVariantSummaryDto(
                            variant.getId(),
                            variant.getName(),
                            sorted.getFirst().quote().effectivePrice(),
                            effectivePrices.isEmpty() ? null : effectivePrices.getFirst(),
                            values.size());
                })
                .toList();
    }

    private Set<ReceiptItem> findOutliers(List<PricedItem> included) {
        Set<ReceiptItem> result = new HashSet<>();
        included.stream()
                .filter(priced -> priced.comparisonPrice() != null && priced.comparisonPrice().signum() > 0
                        && priced.comparisonUnit() != null)
                .collect(Collectors.groupingBy(PricedItem::comparisonUnit))
                .forEach((unit, values) -> markOutliers(values, result));
        return result;
    }

    private void markOutliers(List<PricedItem> values, Set<ReceiptItem> result) {
        if (values.size() < 3) {
            return;
        }
        BigDecimal median = median(values.stream().map(PricedItem::comparisonPrice).sorted().toList());
        if (median.signum() <= 0) {
            return;
        }
        BigDecimal high = median.multiply(OUTLIER_HIGH_FACTOR);
        BigDecimal low = median.multiply(OUTLIER_LOW_FACTOR);
        values.stream()
                .filter(value -> value.comparisonPrice().compareTo(high) > 0 || value.comparisonPrice().compareTo(low) < 0)
                .map(PricedItem::item)
                .forEach(result::add);
    }

    private ProductPriceObservationDto observation(ReceiptItem item, boolean included, boolean outlier) {
        ProductPriceCalculator.PriceQuote quote = ProductPriceCalculator.quote(item);
        ProductFamily family = item.getProductFamily();
        ProductVariant variant = item.getProductVariant();
        Receipt receipt = item.getReceipt();
        return new ProductPriceObservationDto(
                item.getId(),
                receipt == null ? null : receipt.getId(),
                receiptDate(item),
                storeName(item),
                storeBranch(item),
                item.getDescription(),
                family == null ? null : family.getId(),
                family == null ? null : family.getName(),
                variant == null ? null : variant.getId(),
                variant == null ? null : variant.getName(),
                item.getProductAssignmentSource(),
                item.getProductAssignmentStatus(),
                quote.effectivePrice(),
                quote.regularPrice(),
                quote.normalizedUnitPrice(),
                quote.normalizedUnit(),
                included,
                outlier,
                item.isExcludedFromProductPriceComparison(),
                item.getProductPriceExclusionReason());
    }

    private String csv(List<ReceiptItem> items, ProductPriceFilter requestedFilter, boolean useNormalizedUnitPrice) {
        List<PricedItem> values = filtered(items, safeFilter(requestedFilter), useNormalizedUnitPrice);
        Set<ReceiptItem> outliers = findOutliers(values.stream().filter(PricedItem::includedInComparison).toList());
        StringBuilder csv = new StringBuilder("receiptItemId;receiptId;receiptDate;storeName;storeBranch;description;productFamilyId;productFamilyName;productVariantId;productVariantName;productAssignmentSource;productAssignmentStatus;unitPrice;normalizedUnit;effectivePrice;regularPrice;excludedFromProductPriceComparison;exclusionReason;outlier\n");
        values.stream()
                .sorted(Comparator.comparing((PricedItem priced) -> receiptDate(priced.item()), Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(priced -> {
                    ProductPriceObservationDto observation = observation(
                            priced.item(), priced.includedInComparison(), outliers.contains(priced.item()));
                    appendCsvRow(csv,
                            observation.receiptItemId(), observation.receiptId(), observation.receiptDate(),
                            observation.storeName(), observation.storeBranch(), observation.description(),
                            observation.productFamilyId(), observation.productFamilyName(),
                            observation.productVariantId(), observation.productVariantName(),
                            observation.assignmentSource(), observation.assignmentStatus(),
                            observation.normalizedUnitPrice(), observation.normalizedUnit(), observation.effectivePrice(),
                            observation.regularPrice(), observation.excluded(), observation.exclusionReason(), observation.outlier());
                });
        return csv.toString();
    }

    private void appendCsvRow(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                csv.append(';');
            }
            String value = values[index] == null ? "" : values[index].toString();
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append('\n');
    }

    private PageResponse<ProductPriceObservationDto> page(List<ProductPriceObservationDto> content, int requestedPage, int requestedSize) {
        int size = Math.clamp(requestedSize, 1, MAX_PAGE_SIZE);
        int page = Math.max(requestedPage, 0);
        int from = Math.min(page * size, content.size());
        int to = Math.min(from + size, content.size());
        return new PageResponse<>(
                content.subList(from, to),
                page,
                size,
                content.size(),
                (int) Math.ceil((double) content.size() / size),
                "receiptDate",
                "desc");
    }

    private ReceiptItem eligibleItem(Long receiptItemId) {
        return receiptItemRepository.findById(receiptItemId)
                .filter(this::isActiveReceipt)
                .filter(this::isEligible)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Nur bestaetigte oder automatisch zugeordnete Produktpositionen koennen im Preisvergleich verwaltet werden."));
    }

    private void audit(ReceiptItem item, String reason) {
        assignmentLogRepository.save(new ProductAssignmentLog(
                item,
                item.getProductFamily(),
                item.getProductVariant(),
                ProductAssignmentSource.MANUAL,
                item.getProductAssignmentStatus(),
                null,
                "manual-price-review",
                reason));
    }

    private ProductFamily requireFamily(Long productFamilyId) {
        return productFamilyRepository.findById(productFamilyId)
                .orElseThrow(() -> new EntityNotFoundException("Produktfamilie nicht gefunden."));
    }

    private ProductVariant requireVariant(Long productVariantId) {
        return productVariantRepository.findById(productVariantId)
                .orElseThrow(() -> new EntityNotFoundException("Produktvariante nicht gefunden."));
    }

    private LocalDate receiptDate(ReceiptItem item) {
        return item.getReceipt() == null ? null : item.getReceipt().getReceiptDate();
    }

    private String storeName(ReceiptItem item) {
        String name = item.getReceipt() == null ? null : item.getReceipt().getStoreName();
        return name == null || name.isBlank() ? "Unbekanntes Geschäft" : name;
    }

    private String storeBranch(ReceiptItem item) {
        return item.getReceipt() == null ? null : item.getReceipt().getStoreBranch();
    }

    private record PricedItem(
            ReceiptItem item,
            ProductPriceCalculator.PriceQuote quote,
            BigDecimal comparisonPrice,
            String comparisonUnit,
            boolean includedInComparison) {
    }

    private record StoreKey(String storeName, String storeBranch, String label, String unit) {
    }
}
