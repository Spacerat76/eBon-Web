package de.ebon.persistence.repository;

import de.ebon.persistence.model.Receipt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>, JpaSpecificationExecutor<Receipt> {

    Optional<Receipt> findByPaperlessDocumentId(Integer paperlessDocumentId);

    List<Receipt> findByDeletedAtIsNullOrderByImportedAtDesc();

    long countByPaperlessDocumentId(Integer paperlessDocumentId);
}
