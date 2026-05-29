package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.ReceiptItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class ParserServiceImpl implements ParserService {

    @Override
    public Receipt parse(Integer paperlessDocumentId, String rawText) {
        Receipt receipt = new Receipt();
        receipt.setPaperlessDocumentId(paperlessDocumentId);
        receipt.setRawText(rawText == null ? "" : rawText);
        receipt.setImportedAt(OffsetDateTime.now());
        receipt.setParseStatus("PARSED");

        // Minimal parsing: create a single placeholder item
        ReceiptItem item = new ReceiptItem();
        item.setPositionIndex(1);
        item.setDescription("Parsed item");
        item.setTotalPrice(BigDecimal.ZERO);
        receipt.addItem(item);

        return receipt;
    }
}
