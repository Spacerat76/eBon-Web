package de.ebon.api.service;

import de.ebon.api.dto.DataMaintenanceResetRequest;
import de.ebon.api.dto.DataMaintenanceResultDto;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.persistence.repository.SyncLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DataMaintenanceService {

    public static final String RESET_CONFIRMATION = "DELETE_IMPORTED_RECEIPTS";

    private final ReceiptRepository receiptRepository;
    private final SyncLogRepository syncLogRepository;

    public DataMaintenanceService(
            ReceiptRepository receiptRepository,
            SyncLogRepository syncLogRepository) {
        this.receiptRepository = receiptRepository;
        this.syncLogRepository = syncLogRepository;
    }

    @Transactional
    public DataMaintenanceResultDto resetImportedReceipts(DataMaintenanceResetRequest request) {
        if (!RESET_CONFIRMATION.equals(request.confirmation())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bestaetigungstext muss DELETE_IMPORTED_RECEIPTS lauten.");
        }

        long receiptCount = receiptRepository.count();
        long syncLogCount = syncLogRepository.count();
        syncLogRepository.deleteAllInBatch();
        receiptRepository.deleteAllInBatch();

        return new DataMaintenanceResultDto(
                "Importierte Bon-Daten wurden geloescht.",
                receiptCount,
                0,
                0,
                receiptCount,
                syncLogCount);
    }
}
