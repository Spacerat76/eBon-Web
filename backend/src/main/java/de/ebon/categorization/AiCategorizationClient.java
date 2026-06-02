package de.ebon.categorization;

public interface AiCategorizationClient {

    boolean isAvailable();

    AiCategorizationBatchResponse categorize(AiCategorizationBatchRequest request);
}
