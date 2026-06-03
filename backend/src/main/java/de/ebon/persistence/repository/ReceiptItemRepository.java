package de.ebon.persistence.repository;

import de.ebon.persistence.model.ReceiptItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, Long>, JpaSpecificationExecutor<ReceiptItem> {

    List<ReceiptItem> findByReceipt_IdOrderByPositionIndexAsc(Long receiptId);

    boolean existsByCategory_Id(Long categoryId);

    long countByCategory_Id(Long categoryId);

    long countByCategoryIsNullAndReceipt_DeletedAtIsNull();
}
