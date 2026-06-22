package de.ebon.api;

import de.ebon.api.dto.DataMaintenanceResetRequest;
import de.ebon.api.dto.DataMaintenanceResultDto;
import de.ebon.api.dto.ProductDataResetResultDto;
import de.ebon.api.service.DataMaintenanceService;
import de.ebon.api.service.ReceiptApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Datenwartung")
@SecurityRequirement(name = "bearerAuth")
public class DataMaintenanceController {

    private final ReceiptApiService receiptApiService;
    private final DataMaintenanceService dataMaintenanceService;

    public DataMaintenanceController(
            ReceiptApiService receiptApiService,
            DataMaintenanceService dataMaintenanceService) {
        this.receiptApiService = receiptApiService;
        this.dataMaintenanceService = dataMaintenanceService;
    }

    @PostMapping("/api/receipts/reparse")
    @Operation(summary = "Alle importierten Bons erneut parsen")
    public DataMaintenanceResultDto reparseAllReceipts(
            @RequestParam(defaultValue = "false") boolean overwriteManualEdits) {
        return receiptApiService.reparseAllReceipts(overwriteManualEdits);
    }

    @PostMapping("/api/admin/data-reset/imported-receipts")
    @Operation(summary = "Importierte Bon-Daten nach expliziter Bestaetigung loeschen")
    public DataMaintenanceResultDto resetImportedReceipts(
            @Valid @RequestBody DataMaintenanceResetRequest request) {
        return dataMaintenanceService.resetImportedReceipts(request);
    }

    @PostMapping("/api/admin/data-reset/product-data")
    @Operation(summary = "Produkt-Stammdaten und Produktzuordnungen nach expliziter Bestaetigung loeschen")
    public ProductDataResetResultDto resetProductData(
            @Valid @RequestBody DataMaintenanceResetRequest request) {
        return dataMaintenanceService.resetProductData(request);
    }
}
