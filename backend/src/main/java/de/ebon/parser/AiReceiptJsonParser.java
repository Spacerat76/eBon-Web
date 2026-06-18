package de.ebon.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.ParseRuleType;
import de.ebon.persistence.model.ParseSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class AiReceiptJsonParser {

    private final ObjectMapper objectMapper;
    private final ReceiptParseValidator validator = new ReceiptParseValidator();

    AiReceiptJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ReceiptParseResult parse(String json) {
        return parseWithMetadata(json, BigDecimal.ZERO).parseResult();
    }

    AiReceiptJsonParseResult parseWithMetadata(String json, BigDecimal minConfidence) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<ParsedReceiptItem> items = parseItems(required(root, "items"));
            BigDecimal overallConfidence = nullableDecimal(root, "overallConfidence");
            if (overallConfidence == null || overallConfidence.compareTo(minConfidence) < 0) {
                return new AiReceiptJsonParseResult(
                        new ReceiptParseResult(
                                ParseStatus.PARSE_ERROR,
                                null,
                                "KI-Konfidenz ist zu niedrig oder fehlt."),
                        overallConfidence,
                        optionalJson(root, "fieldConfidence"),
                        optionalJson(root, "warnings"),
                        parseRuleSuggestions(root));
            }
            ParsedReceipt receipt = new ParsedReceipt(
                    parseDate(root, "receiptDate"),
                    parseTime(root, "receiptTime"),
                    requiredText(root, "storeName"),
                    nullableText(root, "storeBranch"),
                    requiredDecimal(root, "totalAmount"),
                    nullableText(root, "currency"),
                    nullableDecimal(root, "bonusBalance"),
                    nullableDecimal(root, "bonusPoints"),
                    nullableText(root, "bonusType"),
                    items);
            ReceiptParseResult parseResult = validator.validate(receipt);
            if (parseResult.parsed()) {
                parseResult = parseResult.withParseSource(ParseSource.AI);
            }
            return new AiReceiptJsonParseResult(
                    parseResult,
                    overallConfidence,
                    optionalJson(root, "fieldConfidence"),
                    optionalJson(root, "warnings"),
                    parseRuleSuggestions(root));
        } catch (RuntimeException exception) {
            return invalidResult();
        } catch (Exception exception) {
            return invalidResult();
        }
    }

    private AiReceiptJsonParseResult invalidResult() {
        return new AiReceiptJsonParseResult(
                new ReceiptParseResult(ParseStatus.PARSE_ERROR, null, "KI-JSON entspricht nicht dem erwarteten Parser-Schema."),
                null,
                null,
                null,
                List.of());
    }

    private List<ParsedReceiptItem> parseItems(JsonNode itemsNode) {
        if (itemsNode.size() == 0) {
            throw new IllegalArgumentException("items fehlt oder ist leer.");
        }

        List<ParsedReceiptItem> items = new ArrayList<>();
        for (int index = 0; index < itemsNode.size(); index++) {
            JsonNode item = itemsNode.get(index);
            int positionIndex = requiredInt(item, "positionIndex");
            if (positionIndex != index) {
                throw new IllegalArgumentException("positionIndex ist nicht fortlaufend.");
            }
            items.add(new ParsedReceiptItem(
                    positionIndex,
                    requiredText(item, "description"),
                    nullableDecimal(item, "quantity"),
                    nullableText(item, "unit"),
                    nullableDecimal(item, "unitPrice"),
                    requiredDecimal(item, "totalPrice"),
                    nullableDecimal(item, "discountAmount")));
        }
        return items;
    }

    private LocalDate parseDate(JsonNode node, String field) {
        return LocalDate.parse(requiredText(node, field));
    }

    private LocalTime parseTime(JsonNode node, String field) {
        String value = nullableText(node, field);
        return value == null ? null : LocalTime.parse(value);
    }

    private JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (isMissingOrNull(value)) {
            throw new IllegalArgumentException(field + " fehlt.");
        }
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " fehlt.");
        }
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (isMissingOrNull(value)) {
            return null;
        }
        return value.asString();
    }

    private int requiredInt(JsonNode node, String field) {
        JsonNode value = required(node, field);
        return Integer.parseInt(value.asString());
    }

    private BigDecimal requiredDecimal(JsonNode node, String field) {
        BigDecimal value = nullableDecimal(node, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " fehlt.");
        }
        return value;
    }

    private BigDecimal nullableDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (isMissingOrNull(value)) {
            return null;
        }
        return new BigDecimal(value.asString());
    }

    private List<AiParseRuleSuggestionCandidate> parseRuleSuggestions(JsonNode root) {
        JsonNode suggestionsNode = root.get("parseRuleSuggestions");
        if (isMissingOrNull(suggestionsNode) || suggestionsNode.size() == 0) {
            return List.of();
        }

        List<AiParseRuleSuggestionCandidate> suggestions = new ArrayList<>();
        for (int index = 0; index < suggestionsNode.size(); index++) {
            JsonNode node = suggestionsNode.get(index);
            try {
                suggestions.add(new AiParseRuleSuggestionCandidate(
                        ParseRuleType.valueOf(requiredText(node, "ruleType")),
                        nullableText(node, "storeName"),
                        requiredText(node, "matchRegex"),
                        nullableText(node, "extractGroup"),
                        nullableDecimal(node, "confidence"),
                        requiredText(node, "problemDescription"),
                        requiredText(node, "solutionRationale")));
            } catch (RuntimeException ignored) {
                // Invalid suggestions must not block otherwise valid AI parsing.
            }
        }
        return suggestions;
    }

    private String optionalJson(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return isMissingOrNull(value) ? null : value.toString();
    }

    private boolean isMissingOrNull(JsonNode value) {
        return value == null || "null".equals(value.toString());
    }
}
