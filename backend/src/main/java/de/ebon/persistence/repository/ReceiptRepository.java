package de.ebon.persistence.repository;

import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ParseStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>, JpaSpecificationExecutor<Receipt> {

    Optional<Receipt> findByPaperlessDocumentId(Integer paperlessDocumentId);

    List<Receipt> findByDeletedAtIsNullOrderByImportedAtDesc();

    List<Receipt> findByDeletedAtIsNullAndParseStatus(ParseStatus parseStatus);

    List<Receipt> findByDeletedAtIsNullAndParseStatusAndStoreNameIgnoreCase(ParseStatus parseStatus, String storeName);

    long countByPaperlessDocumentId(Integer paperlessDocumentId);
}
