package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Receipt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParserServiceTest {

    @Test
    void parse_createsReceiptWithParsedStatusAndItem() {
        ParserService parser = new ParserServiceImpl();
        Receipt r = parser.parse(123, "Sample receipt text");

        assertThat(r).isNotNull();
        assertThat(r.getParseStatus()).isEqualTo("PARSED");
        assertThat(r.getItems()).hasSize(1);
        assertThat(r.getPaperlessDocumentId()).isEqualTo(123);
    }
}
