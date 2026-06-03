package de.ebon.api;

import de.ebon.api.dto.ReceiptItemDto;
import de.ebon.api.dto.ReceiptItemUpdateRequest;
import de.ebon.api.service.ReceiptApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Receipt Items")
@SecurityRequirement(name = "bearerAuth")
public class ReceiptItemsController {

    private final ReceiptApiService receiptApiService;

    public ReceiptItemsController(ReceiptApiService receiptApiService) {
        this.receiptApiService = receiptApiService;
    }

    @PatchMapping("/api/receipt-items/{id}")
    @Operation(summary = "Einzelne Position aktualisieren; categoryId=null bedeutet Ohne Kategorie")
    public ReceiptItemDto updateItem(
            @PathVariable Long id,
            @Valid @RequestBody ReceiptItemUpdateRequest request) {
        return receiptApiService.updateItem(id, request);
    }

    @DeleteMapping("/api/receipt-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Position loeschen")
    public void deleteItem(@PathVariable Long id) {
        receiptApiService.deleteItem(id);
    }
}
