package de.ebon.api.service;

import de.ebon.api.dto.DataMaintenanceResetRequest;
import de.ebon.api.dto.DataMaintenanceResultDto;
import de.ebon.api.dto.ProductDataResetResultDto;
import de.ebon.persistence.repository.ProductAssignmentLogRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductRuleRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
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
    private final ReceiptItemRepository receiptItemRepository = mock(ReceiptItemRepository.class);
    private final SyncLogRepository syncLogRepository = mock(SyncLogRepository.class);
    private final ProductAssignmentLogRepository productAssignmentLogRepository = mock(ProductAssignmentLogRepository.class);
    private final ProductRuleRepository productRuleRepository = mock(ProductRuleRepository.class);
    private final ProductVariantRepository productVariantRepository = mock(ProductVariantRepository.class);
    private final ProductFamilyRepository productFamilyRepository = mock(ProductFamilyRepository.class);
    private final DataMaintenanceService service = new DataMaintenanceService(
            receiptRepository,
            syncLogRepository,
            receiptItemRepository,
            productAssignmentLogRepository,
            productRuleRepository,
            productVariantRepository,
            productFamilyRepository);

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

    // Verifies product-data reset needs its own confirmation and keeps imported receipt records intact.
    @Test
    void resetProductDataClearsAssignmentsAndProductReferenceDataAfterItsOwnConfirmation() {
        when(receiptItemRepository.clearProductAssignments()).thenReturn(14);
        when(productAssignmentLogRepository.count()).thenReturn(18L);
        when(productRuleRepository.count()).thenReturn(4L);
        when(productVariantRepository.count()).thenReturn(7L);
        when(productFamilyRepository.count()).thenReturn(3L);

        ProductDataResetResultDto result = service.resetProductData(
                new DataMaintenanceResetRequest(DataMaintenanceService.PRODUCT_DATA_RESET_CONFIRMATION));

        assertThat(result.clearedAssignments()).isEqualTo(14);
        assertThat(result.deletedAssignmentLogs()).isEqualTo(18);
        assertThat(result.deletedProductRules()).isEqualTo(4);
        assertThat(result.deletedProductVariants()).isEqualTo(7);
        assertThat(result.deletedProductFamilies()).isEqualTo(3);
        verify(receiptItemRepository).clearProductAssignments();
        verify(productAssignmentLogRepository).deleteAllInBatch();
        verify(productRuleRepository).deleteAllInBatch();
        verify(productVariantRepository).deleteAllInBatch();
        verify(productFamilyRepository).deleteAllInBatch();
        verify(receiptRepository, never()).deleteAllInBatch();
        verify(syncLogRepository, never()).deleteAllInBatch();
    }
}
