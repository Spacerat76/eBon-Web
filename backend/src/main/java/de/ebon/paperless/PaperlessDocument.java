package de.ebon.paperless;

public record PaperlessDocument(
        Integer id,
        String title,
        String created,
        String content) {
}
