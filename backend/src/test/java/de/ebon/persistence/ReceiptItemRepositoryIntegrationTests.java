package de.ebon.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReceiptItemRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private ProductFamilyRepository productFamilyRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReceiptItemRepository receiptItemRepository;

    @Test
    void groupedProductCountsExcludeItemsFromSoftDeletedReceipts() {
        ProductFamily family = productFamilyRepository.save(new ProductFamily("Repository Count Family", null));
        ProductVariant variant = productVariantRepository.save(new ProductVariant(
                family,
                "Repository Count Variant",
                BigDecimal.ONE,
                "l",
                1,
                null,
                BigDecimal.ONE,
                "l",
                null));

        receiptRepository.save(receiptWithAssignedItem(910_001, family, variant, false));
        receiptRepository.save(receiptWithAssignedItem(910_002, family, variant, true));
        receiptRepository.flush();

        assertThat(receiptItemRepository.countGroupedByProductFamily())
                .filteredOn(count -> count.id().equals(family.getId()))
                .singleElement()
                .satisfies(count -> assertThat(count.count()).isEqualTo(1));
        assertThat(receiptItemRepository.countGroupedByProductVariant())
                .filteredOn(count -> count.id().equals(variant.getId()))
                .singleElement()
                .satisfies(count -> assertThat(count.count()).isEqualTo(1));
    }

    private Receipt receiptWithAssignedItem(
            int paperlessDocumentId,
            ProductFamily family,
            ProductVariant variant,
            boolean deleted) {
        Receipt receipt = new Receipt(paperlessDocumentId, "repository count fixture");
        ReceiptItem item = new ReceiptItem(0, "Assigned product", new BigDecimal("1.99"));
        item.assignProduct(
                family,
                variant,
                ProductAssignmentSource.MANUAL,
                ProductAssignmentStatus.CONFIRMED,
                BigDecimal.ONE);
        receipt.addItem(item);
        if (deleted) {
            receipt.markDeleted(DeleteReason.TAG_REMOVED);
        }
        return receipt;
    }
}
