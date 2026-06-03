package de.ebon.api;

import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ReceiptDto;
import de.ebon.api.dto.ReceiptItemCreateRequest;
import de.ebon.api.dto.ReceiptItemDto;
import de.ebon.api.dto.ReceiptUpdateRequest;
import de.ebon.api.service.ReceiptApiService;
import de.ebon.persistence.model.ParseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Receipts")
@SecurityRequirement(name = "bearerAuth")
public class ReceiptsController {

    private final ReceiptApiService receiptApiService;

    public ReceiptsController(ReceiptApiService receiptApiService) {
        this.receiptApiService = receiptApiService;
    }

    @GetMapping("/api/receipts")
    @Operation(summary = "Bons paginiert abrufen")
    public PageResponse<ReceiptDto> listReceipts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "receiptDate") String sortBy,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String sortDir,
            @RequestParam(required = false) ParseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return receiptApiService.listReceipts(page, size, sortBy, sortDir, status, dateFrom, dateTo, store, includeDeleted);
    }

    @GetMapping("/api/receipts/{id}")
    @Operation(summary = "Bon-Details inklusive Positionen abrufen")
    public ReceiptDto getReceipt(@PathVariable Long id) {
        return receiptApiService.getReceipt(id);
    }

    @PutMapping("/api/receipts/{id}")
    @Operation(summary = "Bon-Metadaten und Positionen aktualisieren")
    public ReceiptDto updateReceipt(@PathVariable Long id, @Valid @RequestBody ReceiptUpdateRequest request) {
        return receiptApiService.updateReceipt(id, request);
    }

    @PostMapping("/api/receipts/{id}/reparse")
    @Operation(summary = "Bon erneut parsen")
    public ReceiptDto reparseReceipt(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean overwriteManualEdits) {
        return receiptApiService.reparseReceipt(id, overwriteManualEdits);
    }

    @DeleteMapping("/api/receipts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bon per Soft-Delete loeschen")
    public void deleteReceipt(@PathVariable Long id) {
        receiptApiService.deleteReceipt(id);
    }

    @PostMapping("/api/receipts/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Neue Position zu Bon hinzufuegen")
    public ReceiptItemDto addItem(
            @PathVariable Long id,
            @Valid @RequestBody ReceiptItemCreateRequest request) {
        return receiptApiService.addItem(id, request);
    }
}
