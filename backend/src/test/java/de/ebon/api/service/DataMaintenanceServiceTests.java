package de.ebon.api.service;

import de.ebon.api.dto.DataMaintenanceResetRequest;
import de.ebon.api.dto.DataMaintenanceResultDto;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.persistence.repository.SyncLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataMaintenanceServiceTests {

    private final ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
    private final SyncLogRepository syncLogRepository = mock(SyncLogRepository.class);
    private final DataMaintenanceService service = new DataMaintenanceService(receiptRepository, syncLogRepository);

    // Verifies the destructive reset cannot run without the explicit confirmation text.
    @Test
    void resetImportedReceiptsRejectsWrongConfirmation() {
        assertThatThrownBy(() -> service.resetImportedReceipts(new DataMaintenanceResetRequest("DELETE")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> assertThat(((ResponseStatusException) throwable).getStatusCode().value())
                        .isEqualTo(400));

        verify(receiptRepository, never()).deleteAllInBatch();
        verify(syncLogRepository, never()).deleteAllInBatch();
    }

    // Verifies reset scope: sync logs and imported receipts are removed while reference data stays untouched.
    @Test
    void resetImportedReceiptsDeletesReceiptsAndSyncLogsAfterConfirmation() {
        when(receiptRepository.count()).thenReturn(81L);
        when(syncLogRepository.count()).thenReturn(3L);

        DataMaintenanceResultDto result = service.resetImportedReceipts(
                new DataMaintenanceResetRequest(DataMaintenanceService.RESET_CONFIRMATION));

        assertThat(result.deletedReceipts()).isEqualTo(81);
        assertThat(result.deletedSyncLogs()).isEqualTo(3);
        verify(syncLogRepository).deleteAllInBatch();
        verify(receiptRepository).deleteAllInBatch();
    }
}
