package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;

public interface ParserService {
    Receipt parse(Integer paperlessDocumentId, String rawText);
}
