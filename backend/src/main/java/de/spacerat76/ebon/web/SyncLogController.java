package de.spacerat76.ebon.web;

import de.spacerat76.ebon.domain.SyncLog;
import de.spacerat76.ebon.domain.SyncLogEntry;
import de.spacerat76.ebon.repository.SyncLogEntryRepository;
import de.spacerat76.ebon.repository.SyncLogRepository;
import de.spacerat76.ebon.web.dto.SyncLogDto;
import de.spacerat76.ebon.web.dto.SyncLogEntryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sync/logs")
@Tag(name = "Sync Logs", description = "Audit logs for sync runs and per-document actions")
public class SyncLogController {

    private final SyncLogRepository syncLogRepository;
    private final SyncLogEntryRepository syncLogEntryRepository;

    public SyncLogController(SyncLogRepository syncLogRepository, SyncLogEntryRepository syncLogEntryRepository) {
        this.syncLogRepository = syncLogRepository;
        this.syncLogEntryRepository = syncLogEntryRepository;
    }

    @GetMapping
    @Operation(summary = "List sync runs")
    public List<SyncLogDto> list() {
        return syncLogRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a sync run with its entries")
    public ResponseEntity<SyncLogDto> get(@PathVariable Long id) {
        Optional<SyncLog> opt = syncLogRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        SyncLog log = opt.get();
        SyncLogDto dto = toDto(log);
        List<SyncLogEntry> entries = syncLogEntryRepository.findBySyncLogIdOrderByIdDesc(id);
        dto.setEntries(entries.stream().map(this::toEntryDto).collect(Collectors.toList()));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "List entries for a sync run")
    public ResponseEntity<List<SyncLogEntryDto>> entries(@PathVariable Long id) {
        if (!syncLogRepository.existsById(id)) return ResponseEntity.notFound().build();
        List<SyncLogEntry> entries = syncLogEntryRepository.findBySyncLogIdOrderByIdDesc(id);
        return ResponseEntity.ok(entries.stream().map(this::toEntryDto).collect(Collectors.toList()));
    }

    private SyncLogDto toDto(SyncLog s) {
        SyncLogDto d = new SyncLogDto();
        d.setId(s.getId());
        d.setStartedAt(s.getStartedAt());
        d.setFinishedAt(s.getFinishedAt());
        d.setStatus(s.getStatus());
        d.setTotalDocuments(s.getTotalDocuments());
        d.setSucceeded(s.getSucceeded());
        d.setFailed(s.getFailed());
        d.setCreatedAt(s.getCreatedAt());
        return d;
    }

    private SyncLogEntryDto toEntryDto(SyncLogEntry e) {
        SyncLogEntryDto d = new SyncLogEntryDto();
        d.setId(e.getId());
        d.setSyncLogId(e.getSyncLog() != null ? e.getSyncLog().getId() : null);
        d.setPaperlessDocumentId(e.getPaperlessDocumentId());
        d.setAction(e.getAction());
        d.setMessage(e.getMessage());
        d.setCreatedAt(e.getCreatedAt());
        return d;
    }
}
