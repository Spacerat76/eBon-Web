package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AiReceiptParsingFallbackTests {

    @Test
    void validAiJsonIsAcceptedWhenRuleParserCannotParseReceipt() {
        ReceiptParserService parser = parserWithAiResponse("""
                {
                  "receiptDate": "2026-06-10",
                  "receiptTime": "10:15:00",
                  "storeName": "KI Markt",
                  "storeBranch": null,
                  "totalAmount": 2.50,
                  "currency": "EUR",
                  "bonusBalance": null,
                  "bonusPoints": null,
                  "bonusType": null,
                  "items": [
                    {
                      "positionIndex": 0,
                      "description": "KI Artikel",
                      "quantity": 1.0,
                      "unit": "Stk",
                      "unitPrice": 2.50,
                      "totalPrice": 2.50,
                      "discountAmount": null
                    }
                  ]
                }
                """);

        ReceiptParseResult result = parser.parse("unstrukturierter bon ohne total");

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.receipt().storeName()).isEqualTo("KI Markt");
        assertThat(result.receipt().items()).singleElement()
                .satisfies(item -> assertThat(item.description()).isEqualTo("KI Artikel"));
    }

    @Test
    void malformedAiJsonIsRejected() {
        ReceiptParserService parser = parserWithAiResponse("{ invalid json");

        ReceiptParseResult result = parser.parse("unstrukturierter bon ohne total");

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.errorMessage()).contains("KI-JSON");
    }

    @Test
    void aiJsonOutsideSchemaIsRejected() {
        ReceiptParserService parser = parserWithAiResponse("""
                {
                  "receiptDate": "2026-06-10",
                  "storeName": "KI Markt",
                  "totalAmount": 2.50,
                  "currency": "EUR",
                  "items": []
                }
                """);

        ReceiptParseResult result = parser.parse("unstrukturierter bon ohne total");

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.errorMessage()).isNotBlank();
    }

    @Test
    void aiJsonWithInvalidPositionIndexIsRejected() {
        ReceiptParserService parser = parserWithAiResponse("""
                {
                  "receiptDate": "2026-06-10",
                  "storeName": "KI Markt",
                  "totalAmount": 2.50,
                  "currency": "EUR",
                  "items": [
                    {
                      "positionIndex": "nicht-numerisch",
                      "description": "KI Artikel",
                      "totalPrice": 2.50
                    }
                  ]
                }
                """);

        ReceiptParseResult result = parser.parse("unstrukturierter bon ohne total");

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.errorMessage()).contains("KI-JSON");
    }

    private ReceiptParserService parserWithAiResponse(String aiJson) {
        return new ReceiptParserService(
                new RuleBasedReceiptParser(),
                rawText -> Optional.of(aiJson),
                new AiReceiptJsonParser(new ObjectMapper()));
    }
}
