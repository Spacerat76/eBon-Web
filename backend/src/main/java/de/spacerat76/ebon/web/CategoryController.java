package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.repository.CategoryRepository;
import de.spacerat76.ebon.web.dto.CategoryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<CategoryDto> list() {
        return categoryRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> get(@PathVariable Long id) {
        Optional<Category> opt = categoryRepository.findById(id);
        return opt.map(c -> ResponseEntity.ok(toDto(c))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CategoryDto> create(@RequestBody CategoryDto dto) {
        Category c = new Category();
        applyDtoToEntity(dto, c);
        Category saved = categoryRepository.save(c);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> update(@PathVariable Long id, @RequestBody CategoryDto dto) {
        Optional<Category> opt = categoryRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Category c = opt.get();
        applyDtoToEntity(dto, c);
        Category saved = categoryRepository.save(c);
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!categoryRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private CategoryDto toDto(Category c) {
        CategoryDto d = new CategoryDto();
        d.setId(c.getId());
        d.setName(c.getName());
        d.setColorHex(c.getColorHex());
        d.setIcon(c.getIcon());
        d.setIsActive(c.getIsActive());
        d.setSortOrder(c.getSortOrder());
        return d;
    }

    private void applyDtoToEntity(CategoryDto dto, Category c) {
        if (dto.getName() != null) c.setName(dto.getName());
        c.setColorHex(dto.getColorHex());
        c.setIcon(dto.getIcon());
        if (dto.getIsActive() != null) c.setIsActive(dto.getIsActive());
        if (dto.getSortOrder() != null) c.setSortOrder(dto.getSortOrder());
    }
}
