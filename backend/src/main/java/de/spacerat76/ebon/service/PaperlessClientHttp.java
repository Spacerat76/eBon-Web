package de.spacerat76.ebon.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class PaperlessClientHttp implements PaperlessClient {

    @Override
    public List<Integer> fetchNewDocumentIds() {
        // Skeleton implementation: no external calls yet
        return Collections.emptyList();
    }

    @Override
    public String fetchDocumentText(Integer documentId) {
        throw new UnsupportedOperationException("Paperless HTTP client not implemented yet");
    }
}
