package de.ebon.paperless;

record PaperlessDocumentResponse(
        Integer id,
        String title,
        String created,
        String content) {

    PaperlessDocument toDocument() {
        return new PaperlessDocument(id, title, created, content);
    }
}
