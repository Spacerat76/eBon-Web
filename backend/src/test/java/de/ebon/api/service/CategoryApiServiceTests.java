package de.ebon.api.service;

import de.ebon.api.dto.CategoryDto;
import de.ebon.api.dto.CategoryRequest;
import de.ebon.categorization.CategoryIconRegistry;
import de.ebon.categorization.CategoryManagementService;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryApiServiceTests {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ReceiptItemRepository receiptItemRepository;

    @Mock
    private CategoryManagementService categoryManagementService;

    private CategoryApiService categoryApiService;

    @BeforeEach
    void setUp() {
        categoryApiService = new CategoryApiService(
                categoryRepository,
                receiptItemRepository,
                categoryManagementService,
                new CategoryIconRegistry());
        lenient().when(receiptItemRepository.countByCategory_Id(anyLong())).thenReturn(0L);
    }

    // Verifies the category list contract for normal UI lists and admin views that include inactive categories.
    @Test
    void listUsesActiveCategoriesByDefaultAndIncludesInactiveWhenRequested() {
        Category active = category(1L, "Aktiv", true, 10);
        Category inactive = category(2L, "Inaktiv", false, 20);
        when(categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(active));
        when(categoryRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(active, inactive));

        List<CategoryDto> activeOnly = categoryApiService.list(false);
        List<CategoryDto> withInactive = categoryApiService.list(true);

        assertThat(activeOnly).extracting(CategoryDto::name).containsExactly("Aktiv");
        assertThat(withInactive).extracting(CategoryDto::name).containsExactly("Aktiv", "Inaktiv");
        verify(categoryRepository).findByActiveTrueOrderBySortOrderAscNameAsc();
        verify(categoryRepository).findAllByOrderBySortOrderAscNameAsc();
    }

    // Verifies create normalization so user-entered category names are stored cleanly and can start inactive.
    @Test
    void createTrimsNameAndCanCreateInactiveCategory() {
        when(categoryRepository.findByNameIgnoreCase("Neue Kategorie")).thenReturn(Optional.empty());
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        CategoryDto created = categoryApiService.create(new CategoryRequest(
                "  Neue Kategorie  ",
                "#123456",
                "tag",
                42,
                false));

        assertThat(created.id()).isEqualTo(99L);
        assertThat(created.name()).isEqualTo("Neue Kategorie");
        assertThat(created.colorHex()).isEqualTo("#123456");
        assertThat(created.icon()).isEqualTo("tag");
        assertThat(created.isActive()).isFalse();
        assertThat(created.sortOrder()).isEqualTo(42);
        verify(categoryRepository).saveAndFlush(any(Category.class));
    }

    // Verifies category icons are chosen from the safe fixed list instead of accepting arbitrary HTML/SVG names.
    @Test
    void createRejectsUnknownIconValues() {
        when(categoryRepository.findByNameIgnoreCase("Neue Kategorie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryApiService.create(new CategoryRequest(
                "Neue Kategorie",
                "#123456",
                "<svg/onload=alert(1)>",
                42,
                true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Kategorie-Icon ist nicht erlaubt.");
    }

    private Category category(long id, String name, boolean active, int sortOrder) {
        Category category = new Category(name, "#111111", "tag", sortOrder);
        ReflectionTestUtils.setField(category, "id", id);
        if (!active) {
            category.deactivate();
        }
        return category;
    }
}
