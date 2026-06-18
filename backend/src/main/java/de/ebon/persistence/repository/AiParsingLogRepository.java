package de.ebon.persistence.repository;

import de.ebon.persistence.model.AiParsingLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiParsingLogRepository extends JpaRepository<AiParsingLog, Long> {

    List<AiParsingLog> findByReceipt_IdOrderByStartedAtDesc(Long receiptId);

    Optional<AiParsingLog> findFirstByReceipt_IdOrderByStartedAtDesc(Long receiptId);

    List<AiParsingLog> findByReceipt_IdOrderByStartedAtDesc(Long receiptId, Pageable pageable);
}
