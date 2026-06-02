package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParseApplierTests {

    private final ReceiptParseApplier applier = new ReceiptParseApplier();

    @Test
    void limitsParsedTextFieldsToDatabaseColumnLengths() {
        Receipt receipt = new Receipt(123, "raw text");
        String longDescription = "Artikel ".repeat(100);
        String longStoreName = "Store ".repeat(80);
        String longUnit = "Packung ".repeat(10);
        ReceiptParseResult result = new ReceiptParseResult(
                ParseStatus.PARSED,
                new ParsedReceipt(
                        LocalDate.of(2026, 6, 2),
                        null,
                        longStoreName,
                        longStoreName,
                        new BigDecimal("1.99"),
                        "EUR",
                        null,
                        null,
                        "REWE Bonus ".repeat(10),
                        List.of(new ParsedReceiptItem(
                                0,
                                longDescription,
                                BigDecimal.ONE,
                                longUnit,
                                new BigDecimal("1.99"),
                                new BigDecimal("1.99"),
                                null))),
                null);

        applier.apply(receipt, result);

        assertThat(receipt.getStoreName()).hasSize(255);
        assertThat(receipt.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getDescription()).hasSizeLessThanOrEqualTo(512);
            assertThat(item.getUnit()).hasSizeLessThanOrEqualTo(32);
        });
    }
}
