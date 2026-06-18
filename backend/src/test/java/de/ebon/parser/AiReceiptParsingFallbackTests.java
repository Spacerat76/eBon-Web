package de.ebon.parser;

import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.ParseStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AiReceiptParsingFallbackTests {

    private final AiReceiptJsonParser parser = new AiReceiptJsonParser(new ObjectMapper());

    // Verifies the controlled AI schema can be adopted only when the JSON is valid and confidence is high enough.
    @Test
    void validAiJsonIsAcceptedWithHighConfidence() {
        AiReceiptJsonParseResult result = parser.parseWithMetadata(validJson("0.950", "2.50"), new BigDecimal("0.900"));

        assertThat(result.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.parseResult().parseSource()).isEqualTo(ParseSource.AI);
        assertThat(result.overallConfidence()).isEqualByComparingTo("0.950");
        assertThat(result.fieldConfidenceJson()).contains("receiptDate");
        assertThat(result.warningsJson()).contains("storeBranch");
        assertThat(result.parseResult().receipt().storeName()).isEqualTo("KI Markt");
        assertThat(result.parseResult().receipt().items()).singleElement()
                .satisfies(item -> assertThat(item.description()).isEqualTo("KI Artikel"));
    }

    // Verifies malformed AI JSON is rejected so external model output cannot silently corrupt parsed data.
    @Test
    void malformedAiJsonIsRejected() {
        AiReceiptJsonParseResult result = parser.parseWithMetadata("{ invalid json", new BigDecimal("0.900"));

        assertThat(result.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.parseResult().errorMessage()).contains("KI-JSON");
    }

    // Verifies schema-invalid AI JSON is rejected even when it is syntactically valid.
    @Test
    void aiJsonOutsideSchemaIsRejected() {
        AiReceiptJsonParseResult result = parser.parseWithMetadata("""
                {
                  "receiptDate": "2026-06-10",
                  "storeName": "KI Markt",
                  "overallConfidence": 0.950,
                  "totalAmount": 2.50,
                  "currency": "EUR",
                  "items": []
                }
                """, new BigDecimal("0.900"));

        assertThat(result.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.parseResult().errorMessage()).isNotBlank();
    }

    // Verifies item-level schema validation rejects invalid position indices from AI output.
    @Test
    void aiJsonWithInvalidPositionIndexIsRejected() {
        AiReceiptJsonParseResult result = parser.parseWithMetadata("""
                {
                  "receiptDate": "2026-06-10",
                  "storeName": "KI Markt",
                  "overallConfidence": 0.950,
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
                """, new BigDecimal("0.900"));

        assertThat(result.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.parseResult().errorMessage()).contains("KI-JSON");
    }

    // Verifies low-confidence AI parses stay rejected even if the receipt fields are otherwise valid.
    @Test
    void lowConfidenceAiJsonIsRejected() {
        AiReceiptJsonParseResult result = parser.parseWithMetadata(validJson("0.700", "2.50"), new BigDecimal("0.900"));

        assertThat(result.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.parseResult().errorMessage()).contains("Konfidenz");
    }

    // Verifies the existing sum-tolerance contract also protects AI output.
    @Test
    void aiJsonViolatingSumToleranceIsRejected() {
        AiReceiptJsonParseResult result = parser.parseWithMetadata(validJson("0.950", "2.00"), new BigDecimal("0.900"));

        assertThat(result.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.parseResult().errorMessage()).contains("Summe");
    }

    private String validJson(String confidence, String itemTotal) {
        return """
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
                  "overallConfidence": %s,
                  "fieldConfidence": { "receiptDate": 0.98, "items": 0.93 },
                  "warnings": ["storeBranch unsicher"],
                  "items": [
                    {
                      "positionIndex": 0,
                      "description": "KI Artikel",
                      "quantity": 1.0,
                      "unit": "Stk",
                      "unitPrice": 2.50,
                      "totalPrice": %s,
                      "discountAmount": null
                    }
                  ],
                  "parseRuleSuggestions": [
                    {
                      "ruleType": "ITEM_PATTERN",
                      "storeName": "KI Markt",
                      "matchRegex": "^(?<description>.+?)\\\\s+(?<total>\\\\d+,\\\\d{2})$",
                      "extractGroup": "description,total",
                      "confidence": 0.910,
                      "problemDescription": "Testproblem",
                      "solutionRationale": "Testloesung"
                    }
                  ]
                }
                """.formatted(confidence, itemTotal);
    }
}
