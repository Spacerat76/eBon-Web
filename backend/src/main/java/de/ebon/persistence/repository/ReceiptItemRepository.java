package de.ebon.persistence.repository;

import de.ebon.persistence.model.ReceiptItem;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, Long>, JpaSpecificationExecutor<ReceiptItem> {

    List<ReceiptItem> findByReceipt_IdOrderByPositionIndexAsc(Long receiptId);

    List<ReceiptItem> findByProductFamily_Id(Long productFamilyId);

    List<ReceiptItem> findByProductVariant_Id(Long productVariantId);

    long countByProductFamily_Id(Long productFamilyId);

    long countByProductVariant_Id(Long productVariantId);

    @Query("select new de.ebon.persistence.repository.IdCount(i.productFamily.id, count(i)) from ReceiptItem i where i.productFamily is not null group by i.productFamily.id")
    List<IdCount> countGroupedByProductFamily();

    @Query("select new de.ebon.persistence.repository.IdCount(i.productVariant.id, count(i)) from ReceiptItem i where i.productVariant is not null group by i.productVariant.id")
    List<IdCount> countGroupedByProductVariant();

    boolean existsByCategory_Id(Long categoryId);

    long countByCategory_Id(Long categoryId);

    long countByCategoryIsNullAndReceipt_DeletedAtIsNull();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update receipt_item
            set product_family_id = null,
                product_variant_id = null,
                product_assignment_source = null,
                product_assignment_status = null,
                product_assignment_confidence = null,
                product_assignment_updated_at = current_timestamp,
                exclude_from_product_price_comparison = false,
                product_price_exclusion_reason = null
            where product_family_id is not null
               or product_variant_id is not null
               or product_assignment_status is not null
               or exclude_from_product_price_comparison = true
            """, nativeQuery = true)
    int clearProductAssignments();
}
