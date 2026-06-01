package de.ebon.paperless;

import java.util.List;

public interface PaperlessClient {

    List<PaperlessDocument> fetchDocumentsByTag();
}
