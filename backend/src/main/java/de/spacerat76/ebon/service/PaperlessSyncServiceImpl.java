package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.repository.ReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaperlessSyncServiceImpl implements PaperlessSyncService {

    private static final Logger log = LoggerFactory.getLogger(PaperlessSyncServiceImpl.class);

    private final PaperlessClient paperlessClient;
    private final ParserService parserService;
    private final ReceiptRepository receiptRepository;

    public PaperlessSyncServiceImpl(PaperlessClient paperlessClient, ParserService parserService, ReceiptRepository receiptRepository) {
        this.paperlessClient = paperlessClient;
        this.parserService = parserService;
        this.receiptRepository = receiptRepository;
    }

    @Override
    public void syncNewDocuments() {
        List<Integer> ids = paperlessClient.fetchNewDocumentIds();
        for (Integer id : ids) {
            try {
                String text = paperlessClient.fetchDocumentText(id);
                Receipt parsed = parserService.parse(id, text);
                receiptRepository.save(parsed);
            } catch (Exception e) {
                log.warn("Failed to sync document {}: {}", id, e.getMessage());
            }
        }
    }
}
