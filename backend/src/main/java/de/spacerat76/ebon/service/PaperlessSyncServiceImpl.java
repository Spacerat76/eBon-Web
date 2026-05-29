package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.repository.ReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class PaperlessSyncServiceImpl implements PaperlessSyncService {

    private static final Logger log = LoggerFactory.getLogger(PaperlessSyncServiceImpl.class);

    private final PaperlessClient paperlessClient;
    private final ParserService parserService;
    private final ReceiptRepository receiptRepository;

    // status tracking
    private volatile OffsetDateTime lastSyncAt;
    private volatile int lastSyncedCount;
    private volatile int lastErrorCount;
    private volatile long lastDurationMs;

    public PaperlessSyncServiceImpl(PaperlessClient paperlessClient, ParserService parserService, ReceiptRepository receiptRepository) {
        this.paperlessClient = paperlessClient;
        this.parserService = parserService;
        this.receiptRepository = receiptRepository;
    }

    @Override
    public void syncNewDocuments() {
        OffsetDateTime start = OffsetDateTime.now();
        int synced = 0;
        int errors = 0;
        List<Integer> ids = paperlessClient.fetchNewDocumentIds();
        for (Integer id : ids) {
            try {
                String text = paperlessClient.fetchDocumentText(id);
                Receipt parsed = parserService.parse(id, text);
                receiptRepository.save(parsed);
                synced++;
            } catch (Exception e) {
                errors++;
                log.warn("Failed to sync document {}: {}", id, e.getMessage());
            }
        }
        this.lastSyncAt = OffsetDateTime.now();
        this.lastSyncedCount = synced;
        this.lastErrorCount = errors;
        this.lastDurationMs = Duration.between(start, this.lastSyncAt).toMillis();
    }

    @Override
    public boolean syncDocument(Integer documentId) {
        OffsetDateTime start = OffsetDateTime.now();
        try {
            String text = paperlessClient.fetchDocumentText(documentId);
            if (text == null) {
                log.warn("No text returned for document {}", documentId);
                this.lastSyncAt = OffsetDateTime.now();
                this.lastSyncedCount = 0;
                this.lastErrorCount = 1;
                this.lastDurationMs = Duration.between(start, this.lastSyncAt).toMillis();
                return false;
            }
            Receipt parsed = parserService.parse(documentId, text);
            receiptRepository.save(parsed);
            this.lastSyncAt = OffsetDateTime.now();
            this.lastSyncedCount = 1;
            this.lastErrorCount = 0;
            this.lastDurationMs = Duration.between(start, this.lastSyncAt).toMillis();
            return true;
        } catch (Exception e) {
            log.warn("Failed to sync document {}: {}", documentId, e.getMessage());
            this.lastSyncAt = OffsetDateTime.now();
            this.lastSyncedCount = 0;
            this.lastErrorCount = 1;
            this.lastDurationMs = Duration.between(start, this.lastSyncAt).toMillis();
            return false;
        }
    }

    @Override
    public SyncStatus getStatus() {
        return new SyncStatus(lastSyncAt, lastSyncedCount, lastErrorCount, lastDurationMs);
    }
}
