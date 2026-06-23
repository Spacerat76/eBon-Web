package de.ebon.persistence.repository;

import de.ebon.persistence.model.ProductAssignmentLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAssignmentLogRepository extends JpaRepository<ProductAssignmentLog, Long> {

    Optional<ProductAssignmentLog> findFirstByReceiptItem_IdOrderByCreatedAtDesc(Long receiptItemId);
}
