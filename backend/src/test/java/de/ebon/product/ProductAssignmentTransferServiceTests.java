package de.ebon.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductAssignmentTransferServiceTests {

    @Test
    void uncertainReparseCannotInheritConfirmedProductAssignment() {
        ProductFamily family = new ProductFamily("Milch", null);
        ReceiptItem previous = item("Milch", BigDecimal.ONE, "l", BigDecimal.ONE);
        previous.assignProduct(family, null, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED, null);
        ReceiptItem uncertain = item("Milch", BigDecimal.ONE, "l", BigDecimal.ONE);
        uncertain.setExtractionStatus(de.ebon.persistence.model.ExtractionStatus.NEEDS_REVIEW);
        new ProductAssignmentTransferService(assignmentLogRepository)
                .transferConfirmedAssignments(List.of(previous), List.of(uncertain));
        assertThat(uncertain.getProductFamily()).isNull();
        assertThat(uncertain.getProductAssignmentSource()).isNull();
        org.mockito.Mockito.verifyNoInteractions(assignmentLogRepository);
    }

    @Mock
    private ProductAssignmentLogRepository assignmentLogRepository;

    // A manual confirmation survives a reparse only when description, price, and amount still identify the same item.
    @Test
    void transfersConfirmedAssignmentToPlausiblyMatchingParsedItem() {
        ProductFamily family = new ProductFamily("Haferdrink", null);
        ProductVariant variant = new ProductVariant(
                family, "Haferdrink 1 l", BigDecimal.ONE, "l", 1, null, BigDecimal.ONE, "l", null);
        ReceiptItem previous = item("Bio Haferdrink", BigDecimal.ONE, "l", new BigDecimal("1.79"));
        previous.assignProduct(family, variant, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED, null);
        ReceiptItem reparsed = item("BIO-HAFERDRINK", BigDecimal.ONE, "l", new BigDecimal("1.79"));

        new ProductAssignmentTransferService(assignmentLogRepository)
                .transferConfirmedAssignments(List.of(previous), List.of(reparsed));

        assertThat(reparsed.getProductFamily()).isSameAs(family);
        assertThat(reparsed.getProductVariant()).isSameAs(variant);
        assertThat(reparsed.getProductAssignmentSource()).isEqualTo(ProductAssignmentSource.MANUAL);
        assertThat(reparsed.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.CONFIRMED);
        verify(assignmentLogRepository).save(any());
    }

    // A same-description item with a changed quantity is deliberately sent to review instead of copying a possibly wrong variant.
    @Test
    void marksPlausibleButIncompatibleReparseMatchForReview() {
        ProductFamily family = new ProductFamily("Haferdrink", null);
        ProductVariant variant = new ProductVariant(
                family, "Haferdrink 1 l", BigDecimal.ONE, "l", 1, null, BigDecimal.ONE, "l", null);
        ReceiptItem previous = item("Bio Haferdrink", BigDecimal.ONE, "l", new BigDecimal("1.79"));
        previous.assignProduct(family, variant, ProductAssignmentSource.MANUAL, ProductAssignmentStatus.CONFIRMED, null);
        ReceiptItem reparsed = item("BIO-HAFERDRINK", new BigDecimal("0.500"), "l", new BigDecimal("1.19"));

        new ProductAssignmentTransferService(assignmentLogRepository)
                .transferConfirmedAssignments(List.of(previous), List.of(reparsed));

        assertThat(reparsed.getProductFamily()).isNull();
        assertThat(reparsed.getProductVariant()).isNull();
        assertThat(reparsed.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.NEEDS_REVIEW);
        verify(assignmentLogRepository).save(any());
    }

    private ReceiptItem item(String description, BigDecimal quantity, String unit, BigDecimal totalPrice) {
        Receipt receipt = new Receipt(990001, "test receipt");
        ReceiptItem item = new ReceiptItem(0, description, totalPrice);
        item.updateParsedValues(quantity, unit, totalPrice, null);
        receipt.addItem(item);
        return item;
    }
}
