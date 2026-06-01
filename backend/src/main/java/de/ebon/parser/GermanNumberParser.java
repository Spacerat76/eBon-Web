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
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".");
        return new BigDecimal(normalized);
    }
}
