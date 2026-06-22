package de.ebon.persistence.repository;

import de.ebon.persistence.model.ProductAssignmentLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAssignmentLogRepository extends JpaRepository<ProductAssignmentLog, Long> {
}
