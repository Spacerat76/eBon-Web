package de.spacerat76.ebon.repository;

import de.spacerat76.ebon.domain.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    Optional<Receipt> findByPaperlessDocumentId(Integer paperlessDocumentId);

    List<Receipt> findByParseStatus(String parseStatus);
}
