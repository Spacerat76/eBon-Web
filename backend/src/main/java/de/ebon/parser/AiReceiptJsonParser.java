package de.ebon.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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
        try {
            JsonNode root = objectMapper.readTree(json);
            List<ParsedReceiptItem> items = parseItems(required(root, "items"));
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
            return validator.validate(receipt);
        } catch (RuntimeException exception) {
            return new ReceiptParseResult(null, null, "KI-JSON entspricht nicht dem erwarteten Parser-Schema.");
        } catch (Exception exception) {
            return new ReceiptParseResult(null, null, "KI-JSON entspricht nicht dem erwarteten Parser-Schema.");
        }
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
        return value.asText();
    }

    private int requiredInt(JsonNode node, String field) {
        JsonNode value = required(node, field);
        return Integer.parseInt(value.asText());
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
        return new BigDecimal(value.asText());
    }

    private boolean isMissingOrNull(JsonNode value) {
        return value == null || "null".equals(value.toString());
    }
}
