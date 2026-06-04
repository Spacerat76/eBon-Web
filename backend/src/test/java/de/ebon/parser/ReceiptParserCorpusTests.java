package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParserCorpusTests {

    private static final Path CORPUS_DIR = Path.of("src/test/resources/corpus");

    private final ReceiptParserService parser = new ReceiptParserService(
            new RuleBasedReceiptParser(),
            rawText -> java.util.Optional.empty(),
            new AiReceiptJsonParser(new ObjectMapper()));
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Verifies every committed corpus fixture matches its expected parsed receipt contract.
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusFixtures")
    void parsesCorpusFixtures(String fixtureName, Path textPath, Path expectedPath) throws Exception {
        ReceiptParseResult result = parser.parse(Files.readString(textPath));
        JsonNode expected = objectMapper.readTree(Files.readString(expectedPath));
        ParseStatus expectedStatus = expectedStatus(expected);

        assertThat(result.parseStatus())
                .as("fixture %s parse status, error=%s, receipt=%s", fixtureName, result.errorMessage(), result.receipt())
                .isEqualTo(expectedStatus);
        if (expectedStatus == ParseStatus.PARSE_ERROR) {
            assertThat(result.errorMessage()).contains(expected.get("expectedErrorContains").asText());
        } else {
            assertThat(result.errorMessage()).isNull();
        }

        assertReceiptMatches(expected, result.receipt());
    }

    // Verifies sum validation rejects materially inconsistent receipts while preserving partial parser output.
    @Test
    void sumMismatchBecomesParseErrorButKeepsPartialParse() {
        String rawText = """
                REWE
                01.06.2026
                Artikel Eins 1,00
                Artikel Zwei 1,00
                Summe 3,00
                """;

        ReceiptParseResult result = parser.parse(rawText);

        assertThat(result.parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.errorMessage()).contains("0.02");
        assertThat(result.receipt().items()).hasSize(2);
    }

    private static Stream<Arguments> corpusFixtures() throws IOException {
        try (Stream<Path> files = Files.list(CORPUS_DIR)) {
            List<Arguments> arguments = files
                    .filter(path -> path.getFileName().toString().endsWith(".expected.json"))
                    .sorted()
                    .map(expectedPath -> {
                        String expectedFileName = expectedPath.getFileName().toString();
                        String fixtureName = expectedFileName.replace(".expected.json", "");
                        return Arguments.of(
                                fixtureName,
                                CORPUS_DIR.resolve(fixtureName + ".txt"),
                                expectedPath);
                    })
                    .toList();
            return arguments.stream();
        }
    }

    private ParseStatus expectedStatus(JsonNode expected) {
        JsonNode expectedParseStatus = expected.get("expectedParseStatus");
        if (expectedParseStatus == null || isJsonNull(expectedParseStatus)) {
            return ParseStatus.PARSED;
        }
        return ParseStatus.valueOf(expectedParseStatus.asText());
    }

    private void assertReceiptMatches(JsonNode expected, ParsedReceipt actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.receiptDate().toString()).isEqualTo(nullableText(expected, "receiptDate"));
        assertThat(actual.receiptTime()).isEqualTo(nullableTime(expected, "receiptTime"));
        assertThat(actual.storeName()).isEqualTo(nullableText(expected, "storeName"));
        assertThat(actual.storeBranch()).isEqualTo(nullableText(expected, "storeBranch"));
        assertDecimal(actual.totalAmount(), expected, "totalAmount");
        assertThat(actual.currency()).isEqualTo(nullableText(expected, "currency"));
        assertDecimal(actual.bonusBalance(), expected, "bonusBalance");
        assertDecimal(actual.bonusPoints(), expected, "bonusPoints");
        assertThat(actual.bonusType()).isEqualTo(nullableText(expected, "bonusType"));

        JsonNode expectedItems = expected.get("items");
        assertThat(actual.items()).hasSize(expectedItems.size());
        for (int index = 0; index < expectedItems.size(); index++) {
            JsonNode expectedItem = expectedItems.get(index);
            ParsedReceiptItem actualItem = actual.items().get(index);
            assertThat(actualItem.positionIndex()).isEqualTo(expectedItem.get("positionIndex").asInt());
            assertThat(actualItem.positionIndex()).isEqualTo(index);
            assertThat(actualItem.description()).isEqualTo(nullableText(expectedItem, "description"));
            assertDecimal(actualItem.quantity(), expectedItem, "quantity");
            assertThat(actualItem.unit()).isEqualTo(nullableText(expectedItem, "unit"));
            assertDecimal(actualItem.unitPrice(), expectedItem, "unitPrice");
            assertDecimal(actualItem.totalPrice(), expectedItem, "totalPrice");
            assertDecimal(actualItem.discountAmount(), expectedItem, "discountAmount");
        }
    }

    private void assertDecimal(BigDecimal actual, JsonNode expected, String field) {
        BigDecimal expectedValue = nullableDecimal(expected, field);
        if (expectedValue == null) {
            assertThat(actual).isNull();
        } else {
            assertThat(actual).isNotNull();
            assertThat(actual.compareTo(expectedValue)).isZero();
        }
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || isJsonNull(value) ? null : value.asText();
    }

    private BigDecimal nullableDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || isJsonNull(value) ? null : new BigDecimal(value.asText());
    }

    private LocalTime nullableTime(JsonNode node, String field) {
        String value = nullableText(node, field);
        return value == null ? null : LocalTime.parse(value);
    }

    private boolean isJsonNull(JsonNode node) {
        return "null".equals(node.toString());
    }
}
