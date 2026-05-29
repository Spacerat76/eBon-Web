package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.repository.ReceiptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaperlessSyncServiceImplTest {

    @Mock
    PaperlessClient paperlessClient;

    @Mock
    ParserService parserService;

    @Mock
    ReceiptRepository receiptRepository;

    @InjectMocks
    PaperlessSyncServiceImpl syncService;

    @Test
    void sync_savesParsedReceipts() {
        when(paperlessClient.fetchNewDocumentIds()).thenReturn(List.of(100, 101));
        when(paperlessClient.fetchDocumentText(100)).thenReturn("text1");
        when(paperlessClient.fetchDocumentText(101)).thenReturn("text2");

        Receipt r1 = new Receipt();
        r1.setPaperlessDocumentId(100);
        Receipt r2 = new Receipt();
        r2.setPaperlessDocumentId(101);

        when(parserService.parse(100, "text1")).thenReturn(r1);
        when(parserService.parse(101, "text2")).thenReturn(r2);

        syncService.syncNewDocuments();

        verify(receiptRepository).save(r1);
        verify(receiptRepository).save(r2);
    }
}
