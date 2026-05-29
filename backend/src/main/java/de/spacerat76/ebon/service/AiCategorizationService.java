package de.spacerat76.ebon.service;

import java.util.List;

public interface AiCategorizationService {
    int categorizeReceipts(List<Long> receiptIds);
    int categorizeAllReceipts();
}
