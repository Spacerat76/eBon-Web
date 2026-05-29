package de.spacerat76.ebon.repository;

import de.spacerat76.ebon.domain.ReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, Long> {
    List<ReceiptItem> findAllByReceiptId(Long receiptId);
}
