package de.ebon.paperless;

import java.util.List;

public interface PaperlessClient {

    List<PaperlessDocument> fetchDocumentsByTag();

    default PaperlessDocument fetchDocumentById(Integer documentId) {
        throw new PaperlessClientException("Paperless-NGX Dokument konnte nicht gelesen werden.");
    }
}
