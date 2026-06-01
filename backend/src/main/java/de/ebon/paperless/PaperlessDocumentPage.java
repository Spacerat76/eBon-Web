package de.ebon.paperless;

import java.util.List;

record PaperlessDocumentPage(
        String next,
        List<PaperlessDocumentResponse> results) {

    List<PaperlessDocumentResponse> safeResults() {
        return results == null ? List.of() : results;
    }
}
