package de.ebon.persistence.repository;

import de.ebon.persistence.model.ReceiptParseTrace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptParseTraceRepository extends JpaRepository<ReceiptParseTrace, Long> {

    List<ReceiptParseTrace> findByReceipt_IdOrderByLineNumberAsc(Long receiptId);

    long countByReceipt_Id(Long receiptId);
}
