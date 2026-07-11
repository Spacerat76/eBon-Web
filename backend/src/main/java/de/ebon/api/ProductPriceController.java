package de.ebon.api;

import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ProductPriceExclusionRequest;
import de.ebon.api.dto.ProductPriceGrouping;
import de.ebon.api.dto.ProductPriceObservationDto;
import de.ebon.api.dto.ProductPriceReportDto;
import de.ebon.product.ProductPriceFilter;
import de.ebon.product.ProductPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Produktpreise")
@SecurityRequirement(name = "bearerAuth")
public class ProductPriceController {

    private final ProductPriceService productPriceService;

    public ProductPriceController(ProductPriceService productPriceService) {
        this.productPriceService = productPriceService;
    }

    @GetMapping("/api/products/families/{productFamilyId}/prices")
    @Operation(summary = "Preisvergleich fuer eine Produktfamilie")
    public ProductPriceReportDto familyReport(
            @PathVariable Long productFamilyId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "STORE") ProductPriceGrouping grouping,
            @RequestParam(defaultValue = "true") boolean includeExcluded) {
        return productPriceService.familyReport(productFamilyId, filter(dateFrom, dateTo, store, grouping, includeExcluded));
    }

    @GetMapping("/api/products/variants/{productVariantId}/prices")
    @Operation(summary = "Preisvergleich fuer eine Produktvariante")
    public ProductPriceReportDto variantReport(
            @PathVariable Long productVariantId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "STORE") ProductPriceGrouping grouping,
            @RequestParam(defaultValue = "true") boolean includeExcluded) {
        return productPriceService.variantReport(productVariantId, filter(dateFrom, dateTo, store, grouping, includeExcluded));
    }

    @GetMapping("/api/products/families/{productFamilyId}/price-observations")
    @Operation(summary = "Preisbeobachtungen einer Produktfamilie")
    public PageResponse<ProductPriceObservationDto> familyObservations(
            @PathVariable Long productFamilyId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "STORE") ProductPriceGrouping grouping,
            @RequestParam(defaultValue = "true") boolean includeExcluded,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return productPriceService.familyObservations(
                productFamilyId, filter(dateFrom, dateTo, store, grouping, includeExcluded), page, size);
    }

    @GetMapping("/api/products/variants/{productVariantId}/price-observations")
    @Operation(summary = "Preisbeobachtungen einer Produktvariante")
    public PageResponse<ProductPriceObservationDto> variantObservations(
            @PathVariable Long productVariantId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "STORE") ProductPriceGrouping grouping,
            @RequestParam(defaultValue = "true") boolean includeExcluded,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return productPriceService.variantObservations(
                productVariantId, filter(dateFrom, dateTo, store, grouping, includeExcluded), page, size);
    }

    @GetMapping("/api/products/families/{productFamilyId}/prices/export")
    @Operation(summary = "Produktpreisvergleich einer Familie als CSV exportieren")
    public ResponseEntity<String> exportFamilyCsv(
            @PathVariable Long productFamilyId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "STORE") ProductPriceGrouping grouping,
            @RequestParam(defaultValue = "true") boolean includeExcluded) {
        return csv(productPriceService.familyCsv(productFamilyId, filter(dateFrom, dateTo, store, grouping, includeExcluded)),
                "product-family-price-comparison.csv");
    }

    @GetMapping("/api/products/variants/{productVariantId}/prices/export")
    @Operation(summary = "Produktpreisvergleich einer Variante als CSV exportieren")
    public ResponseEntity<String> exportVariantCsv(
            @PathVariable Long productVariantId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String store,
            @RequestParam(defaultValue = "STORE") ProductPriceGrouping grouping,
            @RequestParam(defaultValue = "true") boolean includeExcluded) {
        return csv(productPriceService.variantCsv(productVariantId, filter(dateFrom, dateTo, store, grouping, includeExcluded)),
                "product-variant-price-comparison.csv");
    }

    @PostMapping("/api/products/price-observations/{receiptItemId}/exclude")
    @Operation(summary = "Preisbeobachtung mit Begründung vom Vergleich ausschliessen")
    public ProductPriceObservationDto exclude(
            @PathVariable Long receiptItemId,
            @Valid @RequestBody ProductPriceExclusionRequest request) {
        return productPriceService.exclude(receiptItemId, request.reason());
    }

    @PostMapping("/api/products/price-observations/{receiptItemId}/include")
    @Operation(summary = "Preisbeobachtung wieder in den Vergleich aufnehmen")
    public ProductPriceObservationDto include(@PathVariable Long receiptItemId) {
        return productPriceService.include(receiptItemId);
    }

    private ProductPriceFilter filter(
            LocalDate dateFrom,
            LocalDate dateTo,
            String store,
            ProductPriceGrouping grouping,
            boolean includeExcluded) {
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo darf nicht vor dateFrom liegen.");
        }
        return new ProductPriceFilter(dateFrom, dateTo, store, grouping, includeExcluded);
    }

    private ResponseEntity<String> csv(String csv, String filename) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv);
    }
}
