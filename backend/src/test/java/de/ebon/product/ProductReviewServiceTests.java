package de.ebon.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.ebon.api.dto.ProductAssignmentCorrectionRequest;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTests {

    @Mock
    private ReceiptItemRepository receiptItemRepository;

    @Mock
    private ProductAssignmentLogRepository assignmentLogRepository;

    @Mock
    private ProductFamilyRepository productFamilyRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductManagementService productManagementService;

    // Weighted counter goods such as sausage may be valid at family level; they must not create fake variant reviews.
    @Test
    void familyOnlyRuleAssignmentIsNotReviewCandidate() {
        ProductFamily family = family(7L, "Filetraeucherling");
        ReceiptItem item = item(44L, "FILETRAEUCHERL.", "REWE");
        item.assignProduct(family, null, ProductAssignmentSource.RULE, ProductAssignmentStatus.AUTO_ASSIGNED, null);
        when(receiptItemRepository.findAll()).thenReturn(List.of(item));

        var result = service().queue(0, 10, null, null, null, null, null, null, null, null);

        assertThat(result.content()).isEmpty();
    }

    // A family-only item still appears when the assignment itself is explicitly uncertain.
    @Test
    void needsReviewItemWithoutVariantRemainsReviewCandidate() {
        ReceiptItem item = item(45L, "UNBEKANNTES PRODUKT", "REWE");
        item.markProductNeedsReview(new BigDecimal("0.400"));
        when(receiptItemRepository.findAll()).thenReturn(List.of(item));

        var result = service().queue(0, 10, null, null, null, null, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().assignmentStatus()).isEqualTo(ProductAssignmentStatus.NEEDS_REVIEW);
    }

    // Verifies review correction can create the missing product family inline instead of forcing a detour to master data.
    @Test
    void correctionCreatesNewFamilyFromReviewItem() {
        ReceiptItem item = item(46L, "SERVICE GEW", "REWE");
        item.markProductNeedsReview(null);
        ProductFamily savedFamily = family(8L, "REWE Thekenware");
        when(receiptItemRepository.findById(46L)).thenReturn(Optional.of(item));
        when(productFamilyRepository.findByNameIgnoreCase("REWE Thekenware")).thenReturn(Optional.empty());
        when(productFamilyRepository.saveAndFlush(any(ProductFamily.class))).thenReturn(savedFamily);

        var result = service().correct(46L, new ProductAssignmentCorrectionRequest(
                null,
                "REWE Thekenware",
                null,
                false));

        assertThat(result.currentProductFamilyName()).isEqualTo("REWE Thekenware");
        assertThat(result.assignmentSource()).isEqualTo(ProductAssignmentSource.MANUAL);
        assertThat(result.assignmentStatus()).isEqualTo(ProductAssignmentStatus.CONFIRMED);
        verify(productFamilyRepository).saveAndFlush(any(ProductFamily.class));
    }

    // Verifies one manual decision can cover repeated open positions in the same store without overwriting confirmed work.
    @Test
    void correctionCanApplyToSameOpenStoreDescriptionItems() {
        ProductFamily family = family(9L, "Service Thekenware");
        ReceiptItem selected = item(47L, "SERVICE GEW", "REWE");
        selected.markProductNeedsReview(null);
        ReceiptItem sameOpen = item(48L, "SERVICE GEW", "REWE");
        sameOpen.markProductNeedsReview(null);
        ReceiptItem otherStore = item(49L, "SERVICE GEW", "dm");
        otherStore.markProductNeedsReview(null);
        ReceiptItem confirmed = item(50L, "SERVICE GEW", "REWE");
        confirmed.assignProduct(family(10L, "Bestehende Familie"), null, ProductAssignmentSource.MANUAL,
                ProductAssignmentStatus.CONFIRMED, null);
        when(receiptItemRepository.findById(47L)).thenReturn(Optional.of(selected));
        when(productFamilyRepository.findById(9L)).thenReturn(Optional.of(family));
        when(receiptItemRepository.findAll()).thenReturn(List.of(selected, sameOpen, otherStore, confirmed));

        var result = service().correct(47L, new ProductAssignmentCorrectionRequest(
                9L,
                null,
                null,
                true));

        assertThat(result.possibleRetroactiveItems()).isEqualTo(2);
        assertThat(selected.getProductFamily()).isEqualTo(family);
        assertThat(sameOpen.getProductFamily()).isEqualTo(family);
        assertThat(otherStore.getProductFamily()).isNull();
        assertThat(confirmed.getProductFamily().getName()).isEqualTo("Bestehende Familie");
        verify(productFamilyRepository, never()).saveAndFlush(any(ProductFamily.class));
    }

    private ProductReviewService service() {
        return new ProductReviewService(
                receiptItemRepository,
                assignmentLogRepository,
                productFamilyRepository,
                productVariantRepository,
                productManagementService);
    }

    private ProductFamily family(Long id, String name) {
        ProductFamily family = new ProductFamily(name, null);
        ReflectionTestUtils.setField(family, "id", id);
        return family;
    }

    private ReceiptItem item(Long itemId, String description, String storeName) {
        Receipt receipt = new Receipt(910000 + itemId.intValue(), "mock raw text");
        ReflectionTestUtils.setField(receipt, "id", itemId + 1000);
        ReflectionTestUtils.setField(receipt, "receiptDate", LocalDate.of(2026, 1, 9));
        receipt.setStoreName(storeName);
        ReceiptItem item = new ReceiptItem(0, description, new BigDecimal("3.74"));
        ReflectionTestUtils.setField(item, "id", itemId);
        receipt.addItem(item);
        return item;
    }
}
