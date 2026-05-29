package de.spacerat76.ebon.web;

import de.spacerat76.ebon.service.PaperlessSyncService;
import de.spacerat76.ebon.service.SyncStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SyncControllerTest {

    @Mock
    PaperlessSyncService paperlessSyncService;

    @InjectMocks
    SyncController syncController;

    @Test
    void triggerFullSync_triggersServiceAndReturnsAccepted() {
        ResponseEntity<Void> resp = syncController.triggerFullSync();
        assertThat(resp.getStatusCode().value()).isEqualTo(202);
        verify(paperlessSyncService).syncNewDocuments();
    }

    @Test
    void syncDocument_returnsOkWhenServiceSucceeds() {
        when(paperlessSyncService.syncDocument(123)).thenReturn(true);
        ResponseEntity<Void> resp = syncController.syncDocument(123);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(paperlessSyncService).syncDocument(123);
    }

    @Test
    void syncDocument_returns500WhenServiceFails() {
        when(paperlessSyncService.syncDocument(123)).thenReturn(false);
        ResponseEntity<Void> resp = syncController.syncDocument(123);
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        verify(paperlessSyncService).syncDocument(123);
    }

    @Test
    void status_returnsSyncStatus() {
        SyncStatus s = new SyncStatus();
        s.setLastSyncAt(OffsetDateTime.now());
        s.setLastSyncedCount(2);
        s.setLastErrorCount(1);
        s.setLastDurationMs(1500L);

        when(paperlessSyncService.getStatus()).thenReturn(s);

        ResponseEntity<SyncStatus> resp = syncController.status();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getLastSyncedCount()).isEqualTo(2);
        assertThat(resp.getBody().getLastErrorCount()).isEqualTo(1);
        assertThat(resp.getBody().getLastDurationMs()).isEqualTo(1500L);
    }
}
