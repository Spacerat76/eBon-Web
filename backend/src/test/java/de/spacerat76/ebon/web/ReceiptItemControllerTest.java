package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.ReceiptItem;
import de.spacerat76.ebon.repository.CategoryRepository;
import de.spacerat76.ebon.repository.ReceiptItemRepository;
import de.spacerat76.ebon.web.dto.ReceiptItemDto;
import de.spacerat76.ebon.web.dto.ReceiptItemUpdateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReceiptItemControllerTest {

    @Mock
    ReceiptItemRepository itemRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    de.spacerat76.ebon.service.RuleAdaptationService ruleAdaptationService;

    @InjectMocks
    ReceiptItemController controller;

    @Test
    void update_changesFieldsAndMarksManual() {
        Receipt r = new Receipt();
        r.setId(10L);

        ReceiptItem i = new ReceiptItem();
        i.setId(5L);
        i.setReceipt(r);
        i.setDescription("old");
        i.setTotalPrice(new BigDecimal("2.50"));

        when(itemRepository.findById(5L)).thenReturn(Optional.of(i));
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Category cat = new Category();
        cat.setId(2L);
        cat.setName("Drinks");
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(cat));

        ReceiptItemUpdateDto dto = new ReceiptItemUpdateDto();
        dto.setDescription("new desc");
        dto.setTotalPrice(new BigDecimal("3.00"));
        dto.setCategoryId(2L);

        var resp = controller.update(10L, 5L, dto);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        ReceiptItemDto body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getDescription()).isEqualTo("new desc");
        assertThat(body.getTotalPrice()).isEqualTo(new BigDecimal("3.00"));
        assertThat(body.getCategory()).isEqualTo("Drinks");

        verify(itemRepository).save(any());
        verify(ruleAdaptationService).adaptRuleForManualCategorization(any());
    }

    @Test
    void update_returnsNotFound_whenReceiptMismatch() {
        Receipt r = new Receipt();
        r.setId(99L);

        ReceiptItem i = new ReceiptItem();
        i.setId(5L);
        i.setReceipt(r);

        when(itemRepository.findById(5L)).thenReturn(Optional.of(i));

        ReceiptItemUpdateDto dto = new ReceiptItemUpdateDto();
        var resp = controller.update(10L, 5L, dto);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void update_returnsBadRequest_whenCategoryMissing() {
        Receipt r = new Receipt();
        r.setId(10L);

        ReceiptItem i = new ReceiptItem();
        i.setId(6L);
        i.setReceipt(r);

        when(itemRepository.findById(6L)).thenReturn(Optional.of(i));
        when(categoryRepository.findById(42L)).thenReturn(Optional.empty());

        ReceiptItemUpdateDto dto = new ReceiptItemUpdateDto();
        dto.setCategoryId(42L);

        var resp = controller.update(10L, 6L, dto);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }
}
