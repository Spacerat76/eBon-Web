package de.ebon.sync;

import de.ebon.paperless.PaperlessClient;
import de.ebon.paperless.PaperlessClientException;
import de.ebon.paperless.PaperlessDocument;
import de.ebon.parser.ReceiptParseApplier;
import de.ebon.parser.ReceiptParseResult;
import de.ebon.parser.ReceiptParserService;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.SyncLog;
import de.ebon.persistence.model.SyncLogEntry;
import de.ebon.persistence.model.SyncLogEntryAction;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.persistence.repository.SyncLogRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaperlessSyncRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaperlessSyncRunner.class);

    private final PaperlessClient paperlessClient;
    private final ReceiptParserService receiptParserService;
    private final ReceiptParseApplier receiptParseApplier;
    private final ReceiptRepository receiptRepository;
    private final SyncLogRepository syncLogRepository;

    PaperlessSyncRunner(
            PaperlessClient paperlessClient,
            ReceiptParserService receiptParserService,
            ReceiptParseApplier receiptParseApplier,
            ReceiptRepository receiptRepository,
            SyncLogRepository syncLogRepository) {
        this.paperlessClient = paperlessClient;
        this.receiptParserService = receiptParserService;
        this.receiptParseApplier = receiptParseApplier;
        this.receiptRepository = receiptRepository;
        this.syncLogRepository = syncLogRepository;
    }

    @Transactional
    public SyncRunResult run() {
        SyncLog syncLog = syncLogRepository.save(new SyncLog());

        try {
            List<PaperlessDocument> documents = paperlessClient.fetchDocumentsByTag();
            Map<Integer, PaperlessDocument> documentsById = documents.stream()
                    .filter(document -> document.id() != null)
                    .collect(Collectors.toMap(
                            PaperlessDocument::id,
                            Function.identity(),
                            (first, ignored) -> first,
                            LinkedHashMap::new));

            int newDocumentsCount = importNewDocuments(syncLog, documentsById);
            int removedDocumentsCount = documentsById.isEmpty()
                    ? 0
                    : markReceiptsMissingFromPaperless(syncLog, documentsById.keySet());

            syncLog.markSuccess(newDocumentsCount, removedDocumentsCount);
            return SyncRunResult.from(syncLog);
        } catch (PaperlessClientException exception) {
            syncLog.markFailed("Paperless-NGX konnte nicht synchronisiert werden.");
            return SyncRunResult.from(syncLog);
        }
    }

    private int importNewDocuments(SyncLog syncLog, Map<Integer, PaperlessDocument> documentsById) {
        int imported = 0;
        for (PaperlessDocument document : documentsById.values()) {
            if (receiptRepository.countByPaperlessDocumentId(document.id()) > 0) {
                continue;
            }

            Receipt receipt = new Receipt(document.id(), document.content() == null ? "" : document.content());
            ReceiptParseResult parseResult = receiptParserService.parse(receipt.getRawText());
            receiptParseApplier.apply(receipt, parseResult);
            Receipt savedReceipt = receiptRepository.save(receipt);
            syncLog.addEntry(new SyncLogEntry(
                    document.id(),
                    SyncLogEntryAction.IMPORTED,
                    savedReceipt,
                    "Imported from Paperless-NGX"));
            imported++;
        }
        return imported;
    }

    private int markReceiptsMissingFromPaperless(SyncLog syncLog, Set<Integer> fetchedPaperlessIds) {
        int removed = 0;
        for (Receipt receipt : receiptRepository.findByDeletedAtIsNullOrderByImportedAtDesc()) {
            if (fetchedPaperlessIds.contains(receipt.getPaperlessDocumentId())) {
                continue;
            }

            receipt.markDeleted(DeleteReason.TAG_REMOVED);
            syncLog.addEntry(new SyncLogEntry(
                    receipt.getPaperlessDocumentId(),
                    SyncLogEntryAction.TAG_REMOVED,
                    receipt,
                    "Paperless-NGX tag missing after complete pagination"));
            LOGGER.info(
                    "Receipt marked as TAG_REMOVED after Paperless sync. paperlessDocumentId={}, receiptId={}",
                    receipt.getPaperlessDocumentId(),
                    receipt.getId());
            removed++;
        }
        return removed;
    }
}
