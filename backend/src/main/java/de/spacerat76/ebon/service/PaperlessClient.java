package de.spacerat76.ebon.service;

import java.util.List;

public interface PaperlessClient {
    List<Integer> fetchNewDocumentIds();
    String fetchDocumentText(Integer documentId);
}
