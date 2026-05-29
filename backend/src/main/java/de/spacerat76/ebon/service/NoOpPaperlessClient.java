package de.spacerat76.ebon.service;

import java.util.Collections;
import java.util.List;

public class NoOpPaperlessClient implements PaperlessClient {
    @Override
    public List<Integer> fetchNewDocumentIds() {
        return Collections.emptyList();
    }

    @Override
    public String fetchDocumentText(Integer documentId) {
        return null;
    }
}
