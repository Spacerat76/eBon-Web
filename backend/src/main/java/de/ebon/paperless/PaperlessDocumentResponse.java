package de.ebon.paperless;

import java.time.OffsetDateTime;

record PaperlessDocumentResponse(
        Integer id,
        String title,
        OffsetDateTime created,
        String content) {

    PaperlessDocument toDocument() {
        return new PaperlessDocument(id, title, created, content);
    }
}
