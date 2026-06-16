package de.ebon.api;

import de.ebon.api.dto.CategoryDto;
import de.ebon.api.dto.CategoryIconDto;
import de.ebon.api.dto.CategoryPatchRequest;
import de.ebon.api.dto.CategoryRequest;
import de.ebon.api.dto.MessageResponse;
import de.ebon.api.service.CategoryApiService;
import de.ebon.categorization.CategoryDeletionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Kategorien")
@SecurityRequirement(name = "bearerAuth")
public class CategoriesController {

    private final CategoryApiService categoryApiService;

    public CategoriesController(CategoryApiService categoryApiService) {
        this.categoryApiService = categoryApiService;
    }

    @GetMapping("/api/categories")
    @Operation(summary = "Kategorien abrufen")
    public List<CategoryDto> listCategories(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryApiService.list(includeInactive);
    }

    @GetMapping("/api/categories/icons")
    @Operation(summary = "Erlaubte Kategorie-Icons abrufen")
    public List<CategoryIconDto> listCategoryIcons() {
        return categoryApiService.icons();
    }

    @PostMapping("/api/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kategorie anlegen")
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return categoryApiService.create(request);
    }

    @PutMapping("/api/categories/{id}")
    @Operation(summary = "Kategorie aktualisieren")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryApiService.update(id, request);
    }

    @PatchMapping("/api/categories/{id}")
    @Operation(summary = "Kategorie teilweise aktualisieren")
    public CategoryDto patchCategory(@PathVariable Long id, @Valid @RequestBody CategoryPatchRequest request) {
        return categoryApiService.patch(id, request);
    }

    @DeleteMapping("/api/categories/{id}")
    @Operation(summary = "Kategorie physisch loeschen, falls unreferenziert, sonst deaktivieren")
    public MessageResponse deleteCategory(@PathVariable Long id) {
        CategoryDeletionResult result = categoryApiService.deleteOrDeactivate(id);
        return new MessageResponse(result == CategoryDeletionResult.HARD_DELETED
                ? "Kategorie geloescht."
                : "Kategorie ist referenziert und wurde deaktiviert.");
    }
}
