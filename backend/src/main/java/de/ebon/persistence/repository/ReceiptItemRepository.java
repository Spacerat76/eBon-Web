package de.ebon.persistence.repository;

import de.ebon.persistence.model.ReceiptItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, Long> {

    List<ReceiptItem> findByReceipt_IdOrderByPositionIndexAsc(Long receiptId);

    boolean existsByCategory_Id(Long categoryId);
}
