package de.ebon.paperless;

import java.time.OffsetDateTime;

public record PaperlessDocument(
        Integer id,
        String title,
        OffsetDateTime created,
        String content) {
}
