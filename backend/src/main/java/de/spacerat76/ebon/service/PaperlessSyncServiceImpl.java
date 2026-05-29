package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.repository.ReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class PaperlessSyncServiceImpl implements PaperlessSyncService {

    private static final Logger log = LoggerFactory.getLogger(PaperlessSyncServiceImpl.class);

    private final PaperlessClient paperlessClient;
    private final ParserService parserService;
    private final ReceiptRepository receiptRepository;
    private final de.spacerat76.ebon.repository.SyncLogRepository syncLogRepository;
    private final de.spacerat76.ebon.repository.SyncLogEntryRepository syncLogEntryRepository;

    // status tracking
    private volatile OffsetDateTime lastSyncAt;
    private volatile int lastSyncedCount;
    private volatile int lastErrorCount;
    private volatile long lastDurationMs;

    public PaperlessSyncServiceImpl(PaperlessClient paperlessClient, ParserService parserService, ReceiptRepository receiptRepository,
                                    de.spacerat76.ebon.repository.SyncLogRepository syncLogRepository,
                                    de.spacerat76.ebon.repository.SyncLogEntryRepository syncLogEntryRepository) {
        this.paperlessClient = paperlessClient;
        this.parserService = parserService;
        this.receiptRepository = receiptRepository;
        this.syncLogRepository = syncLogRepository;
        this.syncLogEntryRepository = syncLogEntryRepository;
    }

    @Override
    public void syncNewDocuments() {
        OffsetDateTime start = OffsetDateTime.now();
        // create a sync log record
        de.spacerat76.ebon.domain.SyncLog syncLog = new de.spacerat76.ebon.domain.SyncLog();
        syncLog.setStartedAt(start);
        syncLog.setStatus("RUNNING");
        syncLog = syncLogRepository.save(syncLog);
        int synced = 0;
        int errors = 0;
        List<Integer> ids = paperlessClient.fetchNewDocumentIds();
        java.util.Set<Integer> seenIds = new java.util.HashSet<>(ids);
        for (Integer id : ids) {
            try {
                String text = paperlessClient.fetchDocumentText(id);
                Receipt parsed = parserService.parse(id, text);
                // deduplicate by paperless document id: update existing or insert new
                Optional<Receipt> existingOpt = receiptRepository.findByPaperlessDocumentId(id);
                OffsetDateTime now = OffsetDateTime.now();
                String action = "INSERTED";
                if (existingOpt.isPresent()) {
                    // Do not automatically re-import/overwrite existing receipts during the regular sync.
                    // Per spec, re-import must be an explicit action using the per-document sync endpoint.
                    action = "SKIPPED";
                } else {
                    parsed.setImportedAt(now);
                    parsed.setUpdatedAt(now);
                    receiptRepository.save(parsed);
                    action = "INSERTED";
                }
                // record sync log entry for this document
                try {
                    de.spacerat76.ebon.domain.SyncLogEntry entry = new de.spacerat76.ebon.domain.SyncLogEntry();
                    entry.setSyncLog(syncLog);
                    entry.setPaperlessDocumentId(id);
                    entry.setAction(action);
                    entry.setMessage(null);
                    syncLogEntryRepository.save(entry);
                } catch (Exception ex) {
                    log.warn("Failed to write sync log entry for {}: {}", id, ex.getMessage());
                }
                synced++;
            } catch (Exception e) {
                errors++;
                log.warn("Failed to sync document {}: {}", id, e.getMessage());
                try {
                    de.spacerat76.ebon.domain.SyncLogEntry entry = new de.spacerat76.ebon.domain.SyncLogEntry();
                    entry.setSyncLog(syncLog);
                    entry.setPaperlessDocumentId(id);
                    entry.setAction("ERROR");
                    entry.setMessage(e.getMessage());
                    syncLogEntryRepository.save(entry);
                } catch (Exception ex) {
                    log.warn("Failed to write sync log entry for error {}: {}", id, ex.getMessage());
                }
            }
        }
        this.lastSyncAt = OffsetDateTime.now();
        this.lastSyncedCount = synced;
        this.lastErrorCount = errors;
        this.lastDurationMs = Duration.between(start, this.lastSyncAt).toMillis();

        // detect receipts that previously existed but are no longer tagged in Paperless
        try {
            OffsetDateTime now = OffsetDateTime.now();
            for (Receipt r : receiptRepository.findAll()) {
                Integer pid = r.getPaperlessDocumentId();
                if (pid != null && !seenIds.contains(pid)) {
                    // delete receipts that are no longer tagged in Paperless (avoid keeping stale data)
                    try {
                        receiptRepository.delete(r);
                    } catch (Exception ex) {
                        log.warn("Failed to delete orphan receipt {}: {}", pid, ex.getMessage());
                    }
                    try {
                        de.spacerat76.ebon.domain.SyncLogEntry entry = new de.spacerat76.ebon.domain.SyncLogEntry();
                        entry.setSyncLog(syncLog);
                        entry.setPaperlessDocumentId(pid);
                        entry.setAction("TAG_REMOVED");
                        entry.setMessage("Tag removed in Paperless; deleted receipt");
                        syncLogEntryRepository.save(entry);
                    } catch (Exception ex) {
                        log.warn("Failed to write TAG_REMOVED sync log entry for {}: {}", pid, ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to detect/mark TAG_REMOVED receipts: {}", e.getMessage());
        }

        // finalize sync log
        try {
            syncLog.setFinishedAt(OffsetDateTime.now());
            syncLog.setTotalDocuments(ids == null ? 0 : ids.size());
            syncLog.setSucceeded(synced);
            syncLog.setFailed(errors);
            syncLog.setStatus(errors == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
            syncLogRepository.save(syncLog);
        } catch (Exception e) {
            log.warn("Failed to finalize sync log: {}", e.getMessage());
        }
    }

    @Override
    public boolean syncDocument(Integer documentId) {
        OffsetDateTime start = OffsetDateTime.now();
        // create a small sync log run for this single-document re-import
        de.spacerat76.ebon.domain.SyncLog syncLog = new de.spacerat76.ebon.domain.SyncLog();
        syncLog.setStartedAt(start);
        syncLog.setStatus("RUNNING");
        try {
            syncLog = syncLogRepository.save(syncLog);
        } catch (Exception ex) {
            log.warn("Failed to create per-document sync log: {}", ex.getMessage());
        }

        int errors = 0;
        int synced = 0;
        try {
            String text = paperlessClient.fetchDocumentText(documentId);
            if (text == null) {
                log.warn("No text returned for document {}", documentId);
                errors = 1;
                try {
                    de.spacerat76.ebon.domain.SyncLogEntry entry = new de.spacerat76.ebon.domain.SyncLogEntry();
                    entry.setSyncLog(syncLog);
                    entry.setPaperlessDocumentId(documentId);
                    entry.setAction("ERROR");
                    entry.setMessage("No text returned from Paperless");
                    syncLogEntryRepository.save(entry);
                } catch (Exception ex) {
                    log.warn("Failed to write per-document sync log entry for {}: {}", documentId, ex.getMessage());
                }
                this.lastSyncAt = OffsetDateTime.now();
                this.lastSyncedCount = 0;
                this.lastErrorCount = 1;
                this.lastDurationMs = Duration.between(start, this.lastSyncAt).toMillis();
                // finalize sync log
                try {
                    syncLog.setFinishedAt(OffsetDateTime.now());
                    syncLog.setTotalDocuments(1);
                    syncLog.setSucceeded(0);
                    syncLog.setFailed(1);
                    syncLog.setStatus("COMPLETED_WITH_ERRORS");
                    syncLogRepository.save(syncLog);
                } catch (Exception ex) {
                    log.warn("Failed to finalize per-document sync log: {}", ex.getMessage());
                }
                return false;
            }

            Receipt parsed = parserService.parse(documentId, text);
            // deduplicate: update existing or insert
            Optional<Receipt> existingOpt = receiptRepository.findByPaperlessDocumentId(documentId);
            OffsetDateTime now = OffsetDateTime.now();
            String action;
            if (existingOpt.isPresent()) {
                Receipt existing = existingOpt.get();
                existing.setRawText(parsed.getRawText());
                existing.setStoreName(parsed.getStoreName());
                existing.setStoreBranch(parsed.getStoreBranch());
                existing.setReceiptDate(parsed.getReceiptDate());
                existing.setReceiptTime(parsed.getReceiptTime());
                existing.setTotalAmount(parsed.getTotalAmount());
                existing.setCurrency(parsed.getCurrency());
                existing.setBonusBalance(parsed.getBonusBalance());
                existing.setBonusPoints(parsed.getBonusPoints());
                existing.setBonusType(parsed.getBonusType());
                existing.setParseStatus(parsed.getParseStatus());
                existing.setParseErrorMessage(parsed.getParseErrorMessage());
                existing.setUpdatedAt(now);
                existing.getItems().clear();
                if (parsed.getItems() != null) {
                    for (var it : parsed.getItems()) {
                        existing.addItem(it);
                    }
                }
                receiptRepository.save(existing);
                action = "UPDATED";
                synced = 1;
            } else {
                parsed.setImportedAt(now);
                parsed.setUpdatedAt(now);
                receiptRepository.save(parsed);
                action = "INSERTED";
                synced = 1;
            }

            // record per-document sync log entry
            try {
                de.spacerat76.ebon.domain.SyncLogEntry entry = new de.spacerat76.ebon.domain.SyncLogEntry();
                entry.setSyncLog(syncLog);
                entry.setPaperlessDocumentId(documentId);
                entry.setAction(action);
                entry.setMessage(null);
                syncLogEntryRepository.save(entry);
            } catch (Exception ex) {
                log.warn("Failed to write per-document sync log entry for {}: {}", documentId, ex.getMessage());
            }

            this.lastSyncAt = OffsetDateTime.now();
            this.lastSyncedCount = synced;
            this.lastErrorCount = errors;
            this.lastDurationMs = Duration.between(start, this.lastSyncAt).toMillis();

            // finalize sync log
            try {
                syncLog.setFinishedAt(OffsetDateTime.now());
                syncLog.setTotalDocuments(1);
                syncLog.setSucceeded(synced);
                syncLog.setFailed(errors);
                syncLog.setStatus(errors == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
                syncLogRepository.save(syncLog);
            } catch (Exception ex) {
                log.warn("Failed to finalize per-document sync log: {}", ex.getMessage());
            }

            return errors == 0;
        } catch (Exception e) {
            log.warn("Failed to sync document {}: {}", documentId, e.getMessage());
            try {
                de.spacerat76.ebon.domain.SyncLogEntry entry = new de.spacerat76.ebon.domain.SyncLogEntry();
                entry.setSyncLog(syncLog);
                entry.setPaperlessDocumentId(documentId);
                entry.setAction("ERROR");
                entry.setMessage(e.getMessage());
                syncLogEntryRepository.save(entry);
            } catch (Exception ex) {
                log.warn("Failed to write per-document sync log entry for error {}: {}", documentId, ex.getMessage());
            }
            this.lastSyncAt = OffsetDateTime.now();
            this.lastSyncedCount = 0;
            this.lastErrorCount = 1;
            this.lastDurationMs = Duration.between(start, this.lastSyncAt).toMillis();
            try {
                syncLog.setFinishedAt(OffsetDateTime.now());
                syncLog.setTotalDocuments(1);
                syncLog.setSucceeded(0);
                syncLog.setFailed(1);
                syncLog.setStatus("COMPLETED_WITH_ERRORS");
                syncLogRepository.save(syncLog);
            } catch (Exception ex) {
                log.warn("Failed to finalize per-document sync log after error: {}", ex.getMessage());
            }
            return false;
        }
    }

    @Override
    public SyncStatus getStatus() {
        return new SyncStatus(lastSyncAt, lastSyncedCount, lastErrorCount, lastDurationMs);
    }
}
