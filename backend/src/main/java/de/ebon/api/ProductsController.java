package de.ebon.api;

import de.ebon.api.dto.ProductAssignmentRunRequest;
import de.ebon.api.dto.ProductAssignmentRunResponse;
import de.ebon.api.dto.ProductFamilyDto;
import de.ebon.api.dto.ProductFamilyRequest;
import de.ebon.api.dto.ProductRuleDto;
import de.ebon.api.dto.ProductRulePreviewRequest;
import de.ebon.api.dto.ProductRulePreviewResponse;
import de.ebon.api.dto.ProductRuleRequest;
import de.ebon.api.dto.ProductVariantDto;
import de.ebon.api.dto.ProductVariantRequest;
import de.ebon.product.ProductManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Produkte")
@SecurityRequirement(name = "bearerAuth")
public class ProductsController {

    private final ProductManagementService productManagementService;

    public ProductsController(ProductManagementService productManagementService) {
        this.productManagementService = productManagementService;
    }

    @GetMapping("/api/products/families")
    public List<ProductFamilyDto> families() {
        return productManagementService.families();
    }

    @PostMapping("/api/products/families")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductFamilyDto createFamily(@Valid @RequestBody ProductFamilyRequest request) {
        return productManagementService.createFamily(request);
    }

    @PutMapping("/api/products/families/{id}")
    public ProductFamilyDto updateFamily(@PathVariable Long id, @Valid @RequestBody ProductFamilyRequest request) {
        return productManagementService.updateFamily(id, request);
    }

    @GetMapping("/api/products/variants")
    public List<ProductVariantDto> variants(@RequestParam(required = false) Long productFamilyId) {
        return productManagementService.variants(productFamilyId);
    }

    @PostMapping("/api/products/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductVariantDto createVariant(@Valid @RequestBody ProductVariantRequest request) {
        return productManagementService.createVariant(request);
    }

    @PutMapping("/api/products/variants/{id}")
    public ProductVariantDto updateVariant(@PathVariable Long id, @Valid @RequestBody ProductVariantRequest request) {
        return productManagementService.updateVariant(id, request);
    }

    @GetMapping("/api/products/rules")
    public List<ProductRuleDto> rules() {
        return productManagementService.rules();
    }

    @PostMapping("/api/products/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductRuleDto createRule(@Valid @RequestBody ProductRuleRequest request) {
        return productManagementService.createRule(request);
    }

    @PutMapping("/api/products/rules/{id}")
    public ProductRuleDto updateRule(@PathVariable Long id, @Valid @RequestBody ProductRuleRequest request) {
        return productManagementService.updateRule(id, request);
    }

    @DeleteMapping("/api/products/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) {
        productManagementService.deleteRule(id);
    }

    @PostMapping("/api/products/rules/preview")
    @Operation(summary = "Produktregel auf bestehende Positionen vorschauen")
    public ProductRulePreviewResponse previewRule(@Valid @RequestBody ProductRulePreviewRequest request) {
        return new ProductRulePreviewResponse(productManagementService.preview(request));
    }

    @PostMapping("/api/products/rules/{id}/apply")
    public ProductAssignmentRunResponse applyRule(@PathVariable Long id) {
        return new ProductAssignmentRunResponse(productManagementService.applyRule(id));
    }

    @PostMapping("/api/products/assignments/run")
    public ProductAssignmentRunResponse runAssignments(@RequestBody(required = false) ProductAssignmentRunRequest request) {
        return new ProductAssignmentRunResponse(productManagementService.runAssignments(
                request == null ? new ProductAssignmentRunRequest(null, true) : request));
    }
}
