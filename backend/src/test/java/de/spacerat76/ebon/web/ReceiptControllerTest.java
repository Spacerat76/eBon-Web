package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.ReceiptItem;
import de.spacerat76.ebon.repository.ReceiptRepository;
import de.spacerat76.ebon.service.PaperlessSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReceiptControllerTest {

    @Mock
    ReceiptRepository receiptRepository;

    @Mock
    PaperlessSyncService paperlessSyncService;

    @InjectMocks
    ReceiptController receiptController;

    @Test
    void list_returnsReceipts() {
        Receipt r = new Receipt();
        r.setId(1L);
        r.setPaperlessDocumentId(123);
        r.setStoreName("Store A");
        r.setTotalAmount(new BigDecimal("12.34"));
        r.setCurrency("EUR");
        r.setParseStatus("PARSED");

        ReceiptItem item = new ReceiptItem();
        item.setId(11L);
        item.setPositionIndex(1);
        item.setDescription("Milk");
        item.setTotalPrice(new BigDecimal("1.23"));
        Category c = new Category();
        c.setName("Groceries");
        item.setCategory(c);
        r.addItem(item);

        when(receiptRepository.findAll()).thenReturn(List.of(r));

        var dtos = receiptController.list();

        assertThat(dtos).isNotEmpty();
        assertThat(dtos.get(0).getId()).isEqualTo(1L);
        assertThat(dtos.get(0).getStoreName()).isEqualTo("Store A");
        assertThat(dtos.get(0).getItems()).hasSize(1);
        assertThat(dtos.get(0).getItems().get(0).getDescription()).isEqualTo("Milk");
    }

    @Test
    void get_returnsReceiptById() {
        Receipt r = new Receipt();
        r.setId(5L);
        r.setStoreName("POS");
        when(receiptRepository.findById(5L)).thenReturn(Optional.of(r));

        var resp = receiptController.get(5L);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getId()).isEqualTo(5L);
    }

    @Test
    void sync_triggersPaperlessSync() {
        var resp = receiptController.sync();
        assertThat(resp.getStatusCode().value()).isEqualTo(202);
        verify(paperlessSyncService).syncNewDocuments();
    }
}
