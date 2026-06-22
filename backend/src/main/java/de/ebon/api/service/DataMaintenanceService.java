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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DataMaintenanceService {

    public static final String RESET_CONFIRMATION = "DELETE_IMPORTED_RECEIPTS";
    public static final String PRODUCT_DATA_RESET_CONFIRMATION = "DELETE_PRODUCT_DATA";

    private final ReceiptRepository receiptRepository;
    private final SyncLogRepository syncLogRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final ProductAssignmentLogRepository productAssignmentLogRepository;
    private final ProductRuleRepository productRuleRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductFamilyRepository productFamilyRepository;

    public DataMaintenanceService(
            ReceiptRepository receiptRepository,
            SyncLogRepository syncLogRepository,
            ReceiptItemRepository receiptItemRepository,
            ProductAssignmentLogRepository productAssignmentLogRepository,
            ProductRuleRepository productRuleRepository,
            ProductVariantRepository productVariantRepository,
            ProductFamilyRepository productFamilyRepository) {
        this.receiptRepository = receiptRepository;
        this.syncLogRepository = syncLogRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.productAssignmentLogRepository = productAssignmentLogRepository;
        this.productRuleRepository = productRuleRepository;
        this.productVariantRepository = productVariantRepository;
        this.productFamilyRepository = productFamilyRepository;
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

    /**
     * Clears only Phase-15 product metadata. Imported receipts, categories, rules for categories,
     * settings, backups, and Flyway history deliberately remain untouched.
     */
    @Transactional
    public ProductDataResetResultDto resetProductData(DataMaintenanceResetRequest request) {
        if (!PRODUCT_DATA_RESET_CONFIRMATION.equals(request.confirmation())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bestaetigungstext muss DELETE_PRODUCT_DATA lauten.");
        }

        long assignmentLogCount = productAssignmentLogRepository.count();
        long productRuleCount = productRuleRepository.count();
        long productVariantCount = productVariantRepository.count();
        long productFamilyCount = productFamilyRepository.count();

        productAssignmentLogRepository.deleteAllInBatch();
        int clearedAssignments = receiptItemRepository.clearProductAssignments();
        productRuleRepository.deleteAllInBatch();
        productVariantRepository.deleteAllInBatch();
        productFamilyRepository.deleteAllInBatch();

        return new ProductDataResetResultDto(
                "Produkt-Stammdaten und Produktzuordnungen wurden geloescht.",
                clearedAssignments,
                assignmentLogCount,
                productRuleCount,
                productVariantCount,
                productFamilyCount);
    }
}
