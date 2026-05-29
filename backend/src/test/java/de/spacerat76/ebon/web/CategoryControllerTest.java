package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.repository.CategoryRepository;
import de.spacerat76.ebon.web.dto.CategoryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CategoryControllerTest {

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    CategoryController categoryController;

    @Test
    void list_returnsCategories() {
        Category c = new Category();
        c.setId(1L);
        c.setName("Groceries");
        c.setColorHex("#fff");

        when(categoryRepository.findAll()).thenReturn(List.of(c));

        var dtos = categoryController.list();

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getId()).isEqualTo(1L);
        assertThat(dtos.get(0).getName()).isEqualTo("Groceries");
    }

    @Test
    void create_savesCategory() {
        CategoryDto dto = new CategoryDto();
        dto.setName("Drinks");

        when(categoryRepository.save(any())).thenAnswer(invocation -> {
            Category arg = invocation.getArgument(0);
            arg.setId(2L);
            return arg;
        });

        var resp = categoryController.create(dto);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getId()).isEqualTo(2L);
        assertThat(resp.getBody().getName()).isEqualTo("Drinks");
    }

    @Test
    void delete_deletesWhenExists() {
        when(categoryRepository.existsById(1L)).thenReturn(true);

        var resp = categoryController.delete(1L);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(categoryRepository).deleteById(1L);
    }
}
