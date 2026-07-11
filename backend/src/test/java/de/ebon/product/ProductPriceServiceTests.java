package de.ebon.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.ebon.api.dto.ProductPriceGrouping;
import de.ebon.api.dto.ProductPriceObservationDto;
import de.ebon.api.dto.ProductPriceReportDto;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductPriceServiceTests {

    private final ReceiptItemRepository receiptItemRepository = mock(ReceiptItemRepository.class);
    private final ProductFamilyRepository productFamilyRepository = mock(ProductFamilyRepository.class);
    private final ProductVariantRepository productVariantRepository = mock(ProductVariantRepository.class);
    private final ProductAssignmentLogRepository assignmentLogRepository = mock(ProductAssignmentLogRepository.class);
    private final ProductFamily family = mock(ProductFamily.class);
    private ProductPriceService service;

    @BeforeEach
    void setUp() {
        service = new ProductPriceService(
                receiptItemRepository, productFamilyRepository, productVariantRepository, assignmentLogRepository);
        when(family.getId()).thenReturn(10L);
        when(family.getName()).thenReturn("Haferdrink Barista");
        when(productFamilyRepository.findById(10L)).thenReturn(Optional.of(family));
    }

    @Test
    void calculatesLatestMinimumAverageAndMedianWithoutMixingStoreBranches() {
        ReceiptItem first = comparableItem(1L, "REWE", "Filiale A", LocalDate.of(2026, 1, 10), "2.00");
        ReceiptItem second = comparableItem(2L, "REWE", "Filiale B", LocalDate.of(2026, 2, 10), "4.00");
        ReceiptItem third = comparableItem(3L, "dm", "Innenstadt", LocalDate.of(2026, 3, 10), "10.00");
        when(receiptItemRepository.findByProductFamily_Id(10L)).thenReturn(List.of(first, second, third));

        // A family report compares normalized EUR/l prices and keeps its stated store grouping.
        ProductPriceReportDto report = service.familyReport(10L, filter(ProductPriceGrouping.STORE));

        assertThat(report.statistics()).singleElement().satisfies(statistics -> {
            assertThat(statistics.latestPrice()).isEqualByComparingTo("10.00");
            assertThat(statistics.minimumPrice()).isEqualByComparingTo("2.00");
            assertThat(statistics.averagePrice()).isEqualByComparingTo("5.3333");
            assertThat(statistics.medianPrice()).isEqualByComparingTo("4.00");
            assertThat(statistics.observationCount()).isEqualTo(3);
        });
        assertThat(report.stores()).hasSize(2);
        assertThat(report.stores()).filteredOn(store -> store.label().equals("REWE")).singleElement()
                .satisfies(store -> assertThat(store.observationCount()).isEqualTo(2));

        ProductPriceReportDto branchReport = service.familyReport(10L, filter(ProductPriceGrouping.STORE_BRANCH));
        assertThat(branchReport.stores()).hasSize(3);
    }

    @Test
    void marksExtremePricesVisuallyButDoesNotExcludeThemAutomatically() {
        ReceiptItem normalOne = comparableItem(1L, "REWE", null, LocalDate.of(2026, 1, 10), "1.00");
        ReceiptItem normalTwo = comparableItem(2L, "REWE", null, LocalDate.of(2026, 2, 10), "1.00");
        ReceiptItem extreme = comparableItem(3L, "REWE", null, LocalDate.of(2026, 3, 10), "5.00");
        when(receiptItemRepository.findByProductFamily_Id(10L)).thenReturn(List.of(normalOne, normalTwo, extreme));

        // An outlier remains part of the report until a user explicitly excludes it.
        List<ProductPriceObservationDto> observations = service
                .familyObservations(10L, filter(ProductPriceGrouping.STORE), 0, 20)
                .content();

        assertThat(observations).filteredOn(ProductPriceObservationDto::outlier).singleElement()
                .satisfies(observation -> {
                    assertThat(observation.receiptItemId()).isEqualTo(3L);
                    assertThat(observation.excluded()).isFalse();
                    assertThat(observation.includedInComparison()).isTrue();
                });
    }

    @Test
    void keepsUnsafeAndExcludedItemsOutOfAggregatesWhileLeavingThemAuditable() {
        ReceiptItem confirmed = comparableItem(1L, "REWE", null, LocalDate.of(2026, 1, 10), "2.00");
        ReceiptItem excluded = comparableItem(2L, "REWE", null, LocalDate.of(2026, 2, 10), "9.00");
        when(excluded.isExcludedFromProductPriceComparison()).thenReturn(true);
        when(excluded.getProductPriceExclusionReason()).thenReturn("Erfassungsfehler");
        ReceiptItem needsReview = comparableItem(3L, "REWE", null, LocalDate.of(2026, 3, 10), "99.00");
        when(needsReview.getProductAssignmentStatus()).thenReturn(ProductAssignmentStatus.NEEDS_REVIEW);
        when(receiptItemRepository.findByProductFamily_Id(10L)).thenReturn(List.of(confirmed, excluded, needsReview));

        // The detail list remains auditable, but statistics only use a trusted active observation.
        ProductPriceReportDto report = service.familyReport(10L, filter(ProductPriceGrouping.STORE));
        List<ProductPriceObservationDto> observations = service
                .familyObservations(10L, filter(ProductPriceGrouping.STORE), 0, 20)
                .content();

        assertThat(report.statistics()).singleElement().satisfies(statistics -> {
            assertThat(statistics.observationCount()).isEqualTo(1);
            assertThat(statistics.latestPrice()).isEqualByComparingTo("2.00");
        });
        assertThat(observations).hasSize(3);
        assertThat(observations).filteredOn(ProductPriceObservationDto::excluded).singleElement()
                .extracting(ProductPriceObservationDto::exclusionReason)
                .isEqualTo("Erfassungsfehler");
        assertThat(observations).filteredOn(observation -> observation.assignmentStatus() == ProductAssignmentStatus.NEEDS_REVIEW)
                .singleElement()
                .extracting(ProductPriceObservationDto::includedInComparison)
                .isEqualTo(false);
    }

    @Test
    void excludesAndRestoresAnEligibleObservationWithAnAuditLog() {
        ReceiptItem item = comparableItem(1L, "REWE", null, LocalDate.of(2026, 1, 10), "2.00");
        when(receiptItemRepository.findById(1L)).thenReturn(Optional.of(item));

        // Excluding is reversible and each user decision is written to the existing assignment audit log.
        service.exclude(1L, "Falscher Packungspreis");
        service.include(1L);

        verify(item).setProductPriceExclusion(true, "Falscher Packungspreis");
        verify(item).setProductPriceExclusion(false, null);
        verify(assignmentLogRepository, org.mockito.Mockito.times(2)).save(any());
    }

    private ProductPriceFilter filter(ProductPriceGrouping grouping) {
        return new ProductPriceFilter(null, null, null, grouping, true);
    }

    private ReceiptItem comparableItem(
            Long id, String store, String branch, LocalDate date, String effectivePrice) {
        Receipt receipt = mock(Receipt.class);
        when(receipt.getId()).thenReturn(id + 100L);
        when(receipt.getDeletedAt()).thenReturn(null);
        when(receipt.getStoreName()).thenReturn(store);
        when(receipt.getStoreBranch()).thenReturn(branch);
        when(receipt.getReceiptDate()).thenReturn(date);

        ProductVariant variant = mock(ProductVariant.class);
        when(variant.getId()).thenReturn(id + 200L);
        when(variant.getName()).thenReturn("1 l Packung");
        when(variant.getTotalQuantity()).thenReturn(BigDecimal.ONE);
        when(variant.getTotalUnit()).thenReturn("l");

        ReceiptItem item = mock(ReceiptItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getReceipt()).thenReturn(receipt);
        when(item.getDescription()).thenReturn("Haferdrink Barista");
        when(item.getProductFamily()).thenReturn(family);
        when(item.getProductVariant()).thenReturn(variant);
        when(item.getProductAssignmentStatus()).thenReturn(ProductAssignmentStatus.CONFIRMED);
        when(item.getTotalPrice()).thenReturn(new BigDecimal(effectivePrice));
        when(item.isExcludedFromProductPriceComparison()).thenReturn(false);
        return item;
    }
}
