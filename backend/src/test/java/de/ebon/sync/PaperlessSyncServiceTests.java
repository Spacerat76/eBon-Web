package de.ebon.sync;

import de.ebon.paperless.PaperlessClient;
import de.ebon.paperless.PaperlessClientException;
import de.ebon.paperless.PaperlessDocument;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.SyncStatus;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.persistence.repository.SyncLogRepository;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "app.sync.scheduler.enabled=false")
class PaperlessSyncServiceTests extends PostgresIntegrationTestSupport {

    @Autowired
    private PaperlessSyncService syncService;

    @Autowired
    private FakePaperlessClient paperlessClient;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private SyncLogRepository syncLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        paperlessClient.reset();
        jdbcTemplate.execute("truncate sync_log_entry, sync_log, receipt_item, receipt restart identity cascade");
    }

    // Verifies new Paperless documents are imported once and repeated syncs remain idempotent.
    @Test
    void importsNewDocumentsAndKeepsSyncIdempotent() {
        paperlessClient.respondWith(List.of(
                document(1001, "first raw text"),
                document(1002, "second raw text")));

        SyncRunResult firstRun = syncService.synchronize();
        SyncRunResult secondRun = syncService.synchronize();

        assertThat(firstRun.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(firstRun.newDocumentsCount()).isEqualTo(2);
        assertThat(secondRun.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(secondRun.newDocumentsCount()).isZero();
        assertThat(receiptRepository.findAll()).hasSize(2);
        assertThat(receiptRepository.findByPaperlessDocumentId(1001)).get()
                .satisfies(receipt -> {
                    assertThat(receipt.getRawText()).contains("first raw text");
                    assertThat(receipt.getParseStatus()).isEqualTo(ParseStatus.PARSED);
                    assertThat(receipt.getDeletedAt()).isNull();
                });
    }

    // Verifies TAG_REMOVED soft deletes happen only after a successful complete Paperless fetch.
    @Test
    void marksMissingDocumentsAsTagRemovedOnlyAfterSuccessfulFetch() {
        paperlessClient.respondWith(List.of(
                document(2001, "kept"),
                document(2002, "removed later")));
        syncService.synchronize();

        paperlessClient.respondWith(List.of(document(2001, "kept")));
        SyncRunResult secondRun = syncService.synchronize();

        assertThat(secondRun.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(secondRun.removedDocumentsCount()).isEqualTo(1);
        assertThat(receiptRepository.findByPaperlessDocumentId(2001)).get()
                .satisfies(receipt -> assertThat(receipt.getDeletedAt()).isNull());
        assertThat(receiptRepository.findByPaperlessDocumentId(2002)).get()
                .satisfies(receipt -> {
                    assertThat(receipt.getDeletedAt()).isNotNull();
                    assertThat(receipt.getDeleteReason()).isEqualTo(DeleteReason.TAG_REMOVED);
                });
    }

    // Verifies empty or failed Paperless responses never remove local receipts.
    @Test
    void doesNotRemoveReceiptsWhenPaperlessResultIsEmptyOrFails() {
        paperlessClient.respondWith(List.of(document(3001, "active")));
        syncService.synchronize();

        paperlessClient.respondWith(List.of());
        SyncRunResult emptyRun = syncService.synchronize();

        paperlessClient.failWith(new PaperlessClientException("boom"));
        SyncRunResult failedRun = syncService.synchronize();

        assertThat(emptyRun.status()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(emptyRun.removedDocumentsCount()).isZero();
        assertThat(failedRun.status()).isEqualTo(SyncStatus.FAILED);
        assertThat(receiptRepository.findByPaperlessDocumentId(3001)).get()
                .satisfies(receipt -> assertThat(receipt.getDeletedAt()).isNull());
        assertThat(syncLogRepository.findFirstByOrderByStartedAtDesc()).get()
                .satisfies(syncLog -> assertThat(syncLog.getStatus()).isEqualTo(SyncStatus.FAILED));
    }

    // Verifies the sync lock rejects concurrent runs so two imports cannot race each other.
    @Test
    void rejectsParallelSyncRuns() throws Exception {
        paperlessClient.blockWith(List.of(document(4001, "blocked")));

        syncService.triggerAsync();
        assertThat(paperlessClient.awaitFetchStarted()).isTrue();

        assertThatThrownBy(syncService::triggerAsync)
                .isInstanceOf(SyncAlreadyRunningException.class);

        paperlessClient.releaseBlockedFetch();
        waitUntilSyncFinished();

        assertThat(syncService.currentStatus().isSyncing()).isFalse();
        assertThat(receiptRepository.findByPaperlessDocumentId(4001)).isPresent();
    }

    private void waitUntilSyncFinished() throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (!syncService.currentStatus().isSyncing()) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private static PaperlessDocument document(int id, String content) {
        String rawText = """
                REWE
                01.01.2026
                %s 1,00
                Summe 1,00
                """.formatted(content);
        return new PaperlessDocument(id, "Document " + id, "2026-01-01", rawText);
    }

    @TestConfiguration
    static class FakePaperlessClientConfig {

        @Bean
        @Primary
        FakePaperlessClient fakePaperlessClient() {
            return new FakePaperlessClient();
        }
    }

    static class FakePaperlessClient implements PaperlessClient {

        private List<PaperlessDocument> documents = List.of();
        private RuntimeException exception;
        private CountDownLatch fetchStarted;
        private CountDownLatch releaseFetch;

        @Override
        public List<PaperlessDocument> fetchDocumentsByTag() {
            if (fetchStarted != null) {
                fetchStarted.countDown();
            }
            if (releaseFetch != null) {
                awaitRelease();
            }
            if (exception != null) {
                throw exception;
            }
            return documents;
        }

        void respondWith(List<PaperlessDocument> documents) {
            this.documents = documents;
            this.exception = null;
            this.fetchStarted = null;
            this.releaseFetch = null;
        }

        void failWith(RuntimeException exception) {
            this.exception = exception;
            this.documents = List.of();
            this.fetchStarted = null;
            this.releaseFetch = null;
        }

        void blockWith(List<PaperlessDocument> documents) {
            this.documents = documents;
            this.exception = null;
            this.fetchStarted = new CountDownLatch(1);
            this.releaseFetch = new CountDownLatch(1);
        }

        boolean awaitFetchStarted() throws InterruptedException {
            return fetchStarted.await(5, TimeUnit.SECONDS);
        }

        void releaseBlockedFetch() {
            releaseFetch.countDown();
        }

        void reset() {
            if (releaseFetch != null) {
                releaseFetch.countDown();
            }
            respondWith(List.of());
        }

        private void awaitRelease() {
            try {
                releaseFetch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PaperlessClientException("Interrupted while waiting for fake Paperless response.", exception);
            }
        }
    }
}
