package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.SyncLog;
import de.spacerat76.ebon.domain.SyncLogEntry;
import de.spacerat76.ebon.repository.SyncLogEntryRepository;
import de.spacerat76.ebon.repository.SyncLogRepository;
import de.spacerat76.ebon.web.dto.SyncLogDto;
import de.spacerat76.ebon.web.dto.SyncLogEntryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SyncLogControllerTest {

    @Mock
    SyncLogRepository syncLogRepository;

    @Mock
    SyncLogEntryRepository syncLogEntryRepository;

    @InjectMocks
    SyncLogController controller;

    @Test
    void list_returnsSyncLogs() {
        SyncLog s = new SyncLog();
        s.setId(1L);
        s.setStatus("COMPLETED");
        s.setStartedAt(OffsetDateTime.now());
        when(syncLogRepository.findAll()).thenReturn(List.of(s));

        List<SyncLogDto> list = controller.list();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void get_returnsSyncLogWithEntries() {
        SyncLog s = new SyncLog();
        s.setId(2L);
        s.setStatus("COMPLETED");
        when(syncLogRepository.findById(2L)).thenReturn(Optional.of(s));

        SyncLogEntry e = new SyncLogEntry();
        e.setId(10L);
        e.setPaperlessDocumentId(123);
        e.setAction("INSERTED");
        e.setSyncLog(s);
        when(syncLogEntryRepository.findBySyncLogIdOrderByIdDesc(2L)).thenReturn(List.of(e));

        ResponseEntity<SyncLogDto> resp = controller.get(2L);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        SyncLogDto body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(2L);
        assertThat(body.getEntries()).hasSize(1);
        SyncLogEntryDto entryDto = body.getEntries().get(0);
        assertThat(entryDto.getPaperlessDocumentId()).isEqualTo(123);
    }

    @Test
    void entries_returnsNotFoundWhenLogMissing() {
        when(syncLogRepository.existsById(5L)).thenReturn(false);
        ResponseEntity<List<SyncLogEntryDto>> resp = controller.entries(5L);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }
}
