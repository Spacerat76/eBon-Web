package de.ebon.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.ebon.api.dto.AuditExpectedProductAssignment;
import de.ebon.api.dto.AuditProductCorrectionRequest;
import de.ebon.api.dto.AuditProductVariantRequest;
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
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuditProductCorrectionServiceTests {

    @Mock
    ReceiptItemRepository receiptItemRepository;

    @Mock
    ProductFamilyRepository productFamilyRepository;

    @Mock
    ProductVariantRepository productVariantRepository;

    @Mock
    ProductAssignmentLogRepository assignmentLogRepository;

    @Test
    void assignsExistingFamilyAsAiAutoAssignedAndWritesSanitizedAuditLog() {
        ReceiptItem item = item(41L);
        ProductFamily family = family(7L, "Haferdrink");
        when(receiptItemRepository.findByIdForProductAudit(41L)).thenReturn(Optional.of(item));
        when(productFamilyRepository.findById(7L)).thenReturn(Optional.of(family));

        var result = service().correct(41L, request(expected(null, null, null, null), 7L, null, null, null));

        assertThat(result.productFamilyId()).isEqualTo(7L);
        assertThat(result.source()).isEqualTo(ProductAssignmentSource.AI);
        assertThat(result.status()).isEqualTo(ProductAssignmentStatus.AUTO_ASSIGNED);
        assertThat(result.confidence()).isEqualByComparingTo("0.990");
        ArgumentCaptor<ProductAssignmentLog> log = ArgumentCaptor.forClass(ProductAssignmentLog.class);
        verify(assignmentLogRepository).save(log.capture());
        assertThat(log.getValue().getSource()).isEqualTo(ProductAssignmentSource.AI);
        assertThat(log.getValue().getStatus()).isEqualTo(ProductAssignmentStatus.AUTO_ASSIGNED);
        assertThat(ReflectionTestUtils.getField(log.getValue(), "modelUsed")).isEqualTo("codex-interactive-audit");
        assertThat(log.getValue().getDecisionReason()).isEqualTo("UNIQUE_EXISTING_FAMILY");
    }

    @Test
    void createsNewFamilyAndVariantTransactionallyWithoutManualProvenance() {
        ReceiptItem item = item(42L);
        item.markProductNeedsReview(null);
        ProductFamily savedFamily = family(8L, "Bio Mandeln");
        ProductVariant savedVariant = variant(9L, savedFamily, "Bio Mandeln 500 g");
        when(receiptItemRepository.findByIdForProductAudit(42L)).thenReturn(Optional.of(item));
        when(productFamilyRepository.findByNameIgnoreCase("Bio Mandeln")).thenReturn(Optional.empty());
        when(productFamilyRepository.saveAndFlush(any(ProductFamily.class))).thenReturn(savedFamily);
        when(productVariantRepository.saveAndFlush(any(ProductVariant.class))).thenReturn(savedVariant);
        var variant = new AuditProductVariantRequest(
                "Bio Mandeln 500 g", new BigDecimal("500.000"), "g", 1, null,
                new BigDecimal("500.000"), "g", null);

        var result = service().correct(42L, request(
                expected(null, null, null, ProductAssignmentStatus.NEEDS_REVIEW), null, "Bio Mandeln", null, variant));

        assertThat(result.familyCreated()).isTrue();
        assertThat(result.variantCreated()).isTrue();
        assertThat(result.productFamilyId()).isEqualTo(8L);
        assertThat(result.productVariantId()).isEqualTo(9L);
        assertThat(item.getProductAssignmentSource()).isEqualTo(ProductAssignmentSource.AI);
        assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.AUTO_ASSIGNED);
    }

    @Test
    void refusesManualOrConfirmedAssignmentsWithoutChangingThem() {
        ProductFamily manualFamily = family(3L, "Manuell");
        ReceiptItem item = item(43L);
        item.assignProduct(manualFamily, null, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED, null);
        when(receiptItemRepository.findByIdForProductAudit(43L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service().correct(43L, request(
                expected(3L, null, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED),
                7L, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("geschützt");
        verify(productFamilyRepository, never()).findById(7L);
        verify(assignmentLogRepository, never()).save(any());
    }

    @Test
    void refusesAStaleExpectedAssignmentTuple() {
        ReceiptItem item = item(44L);
        when(receiptItemRepository.findByIdForProductAudit(44L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service().correct(44L, request(
                expected(99L, null, null, null), 7L, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("verändert");
        verify(assignmentLogRepository, never()).save(any());
    }

    @Test
    void refusesInactiveOrWrongFamilyVariantTargets() {
        ReceiptItem item = item(45L);
        ProductFamily family = family(7L, "Getränk");
        ProductFamily otherFamily = family(8L, "Andere Familie");
        ProductVariant wrongVariant = variant(9L, otherFamily, "Andere Variante");
        when(receiptItemRepository.findByIdForProductAudit(45L)).thenReturn(Optional.of(item));
        when(productFamilyRepository.findById(7L)).thenReturn(Optional.of(family));
        when(productVariantRepository.findById(9L)).thenReturn(Optional.of(wrongVariant));

        assertThatThrownBy(() -> service().correct(45L, request(
                expected(null, null, null, null), 7L, null, 9L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("gehört nicht");
    }

    private AuditProductCorrectionService service() {
        return new AuditProductCorrectionService(
                receiptItemRepository, productFamilyRepository, productVariantRepository, assignmentLogRepository);
    }

    private AuditProductCorrectionRequest request(
            AuditExpectedProductAssignment expected,
            Long familyId,
            String newFamily,
            Long variantId,
            AuditProductVariantRequest newVariant) {
        return new AuditProductCorrectionRequest(
                expected, familyId, newFamily, variantId, newVariant,
                new BigDecimal("0.990"), "UNIQUE_EXISTING_FAMILY");
    }

    private AuditExpectedProductAssignment expected(
            Long familyId,
            Long variantId,
            ProductAssignmentSource source,
            ProductAssignmentStatus status) {
        return new AuditExpectedProductAssignment(familyId, variantId, source, status);
    }

    private ReceiptItem item(Long id) {
        Receipt receipt = new Receipt(920000 + id.intValue(), "private test receipt");
        ReflectionTestUtils.setField(receipt, "id", id + 1000);
        ReceiptItem item = new ReceiptItem(0, "TEST POSITION", new BigDecimal("1.99"));
        ReflectionTestUtils.setField(item, "id", id);
        receipt.addItem(item);
        return item;
    }

    private ProductFamily family(Long id, String name) {
        ProductFamily family = new ProductFamily(name, null);
        ReflectionTestUtils.setField(family, "id", id);
        return family;
    }

    private ProductVariant variant(Long id, ProductFamily family, String name) {
        ProductVariant variant = new ProductVariant(family, name, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(variant, "id", id);
        return variant;
    }
}
