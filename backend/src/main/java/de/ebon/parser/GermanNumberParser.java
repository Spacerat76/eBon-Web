package de.ebon.parser;

import java.math.BigDecimal;

final class GermanNumberParser {

    private GermanNumberParser() {
    }

    static BigDecimal parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace("EUR", "")
                .replace("*", "")
                .replace(" ", "");
        int lastComma = normalized.lastIndexOf(',');
        int lastDot = normalized.lastIndexOf('.');
        if (lastComma >= 0) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (lastDot >= 0 && !normalized.matches("-?\\d+\\.\\d{2}")) {
            normalized = normalized.replace(".", "");
        }
        return new BigDecimal(normalized);
    }
}
