package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    de.spacerat76.ebon.repository.SyncLogRepository syncLogRepository;

    @Mock
    de.spacerat76.ebon.repository.SyncLogEntryRepository syncLogEntryRepository;

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
        when(receiptRepository.findByPaperlessDocumentId(100)).thenReturn(Optional.empty());
        when(receiptRepository.findByPaperlessDocumentId(101)).thenReturn(Optional.empty());
        when(syncLogRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        when(syncLogEntryRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        syncService.syncNewDocuments();
        verify(receiptRepository).save(r1);
        verify(receiptRepository).save(r2);
        verify(syncLogRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
        verify(syncLogEntryRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sync_updatesExistingReceipt_whenAlreadyPresent() {
        when(paperlessClient.fetchNewDocumentIds()).thenReturn(List.of(200));
        when(paperlessClient.fetchDocumentText(200)).thenReturn("text200");

        Receipt parsed = new Receipt();
        parsed.setPaperlessDocumentId(200);
        parsed.setStoreName("NewStore");
        parsed.setRawText("raw200");

        Receipt existing = new Receipt();
        existing.setPaperlessDocumentId(200);
        existing.setStoreName("OldStore");
        existing.setRawText("oldraw");

        when(parserService.parse(200, "text200")).thenReturn(parsed);
        when(receiptRepository.findByPaperlessDocumentId(200)).thenReturn(Optional.of(existing));
        when(syncLogRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        when(syncLogEntryRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        syncService.syncNewDocuments();

        // Existing receipts should NOT be automatically overwritten during the regular sync
        verify(receiptRepository, org.mockito.Mockito.never()).save(existing);
        assertThat(existing.getStoreName()).isEqualTo("OldStore");
        assertThat(existing.getRawText()).isEqualTo("oldraw");
        verify(syncLogRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
        // A single sync log entry should be written (SKIPPED)
        verify(syncLogEntryRepository, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sync_marksReceiptsWhenTagRemoved() {
        when(paperlessClient.fetchNewDocumentIds()).thenReturn(List.of(100));
        when(paperlessClient.fetchDocumentText(100)).thenReturn("text1");

        Receipt parsed = new Receipt();
        parsed.setPaperlessDocumentId(100);
        when(parserService.parse(100, "text1")).thenReturn(parsed);

        Receipt orphan = new Receipt();
        orphan.setPaperlessDocumentId(200);
        orphan.setParseStatus("PARSED");

        when(receiptRepository.findByPaperlessDocumentId(100)).thenReturn(Optional.empty());
        when(receiptRepository.findAll()).thenReturn(List.of(orphan));
        when(syncLogRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        when(syncLogEntryRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        syncService.syncNewDocuments();

        // orphan should be marked and saved
        verify(receiptRepository).save(orphan);
        assertThat(orphan.getParseStatus()).isEqualTo("TAG_REMOVED");
        verify(syncLogRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
        verify(syncLogEntryRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void syncDocument_updatesExisting_andRecordsUpdatedEntry() {
        Integer docId = 300;
        when(paperlessClient.fetchDocumentText(docId)).thenReturn("text300");

        Receipt parsed = new Receipt();
        parsed.setPaperlessDocumentId(docId);
        parsed.setStoreName("NewStore300");

        Receipt existing = new Receipt();
        existing.setPaperlessDocumentId(docId);
        existing.setStoreName("OldStore300");

        when(parserService.parse(docId, "text300")).thenReturn(parsed);
        when(receiptRepository.findByPaperlessDocumentId(docId)).thenReturn(Optional.of(existing));
        when(syncLogRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        when(syncLogEntryRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        boolean ok = syncService.syncDocument(docId);

        assertThat(ok).isTrue();
        verify(receiptRepository).save(existing);
        // verify a sync log entry was written with action "UPDATED"
        org.mockito.ArgumentCaptor<de.spacerat76.ebon.domain.SyncLogEntry> captor = org.mockito.ArgumentCaptor.forClass(de.spacerat76.ebon.domain.SyncLogEntry.class);
        verify(syncLogEntryRepository).save(captor.capture());
        de.spacerat76.ebon.domain.SyncLogEntry entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo("UPDATED");
        assertThat(entry.getPaperlessDocumentId()).isEqualTo(docId);
    }

    @Test
    void syncDocument_insertsNew_andRecordsInsertedEntry() {
        Integer docId = 400;
        when(paperlessClient.fetchDocumentText(docId)).thenReturn("text400");

        Receipt parsed = new Receipt();
        parsed.setPaperlessDocumentId(docId);
        parsed.setStoreName("Store400");

        when(parserService.parse(docId, "text400")).thenReturn(parsed);
        when(receiptRepository.findByPaperlessDocumentId(docId)).thenReturn(Optional.empty());
        when(syncLogRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        when(syncLogEntryRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        boolean ok = syncService.syncDocument(docId);

        assertThat(ok).isTrue();
        org.mockito.ArgumentCaptor<de.spacerat76.ebon.domain.SyncLogEntry> captor = org.mockito.ArgumentCaptor.forClass(de.spacerat76.ebon.domain.SyncLogEntry.class);
        verify(syncLogEntryRepository).save(captor.capture());
        de.spacerat76.ebon.domain.SyncLogEntry entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo("INSERTED");
        assertThat(entry.getPaperlessDocumentId()).isEqualTo(docId);
    }
}
