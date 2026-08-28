package de.ebon.persistence.repository;

import de.ebon.persistence.model.ReceiptParseTrace;
import de.ebon.persistence.model.ParseLineType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptParseTraceRepository extends JpaRepository<ReceiptParseTrace, Long> {

    List<ReceiptParseTrace> findByReceipt_IdOrderByLineNumberAsc(Long receiptId);

    long countByReceipt_Id(Long receiptId);

    long countByReceipt_IdAndLineType(Long receiptId, ParseLineType lineType);
}
