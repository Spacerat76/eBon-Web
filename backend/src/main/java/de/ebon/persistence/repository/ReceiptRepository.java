package de.ebon.persistence.repository;

import de.ebon.persistence.model.Receipt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByPaperlessDocumentId(Integer paperlessDocumentId);

    List<Receipt> findByDeletedAtIsNullOrderByImportedAtDesc();
}
