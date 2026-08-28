package de.ebon.parser.profile;

import java.util.Map;

public record ProfileItemRule(
        String regex,
        Map<Field, Integer> captures,
        ProfileItemRegion region,
        ProfileMultilineRule multiline,
        Type type) {
    public ProfileItemRule {
        captures = Map.copyOf(captures == null ? Map.of() : captures);
    }

    public enum Field {
        DESCRIPTION, QUANTITY, UNIT, UNIT_PRICE, TOTAL_PRICE, DISCOUNT_AMOUNT
    }

    public enum Type {
        ITEM, DISCOUNT, DEPOSIT, COUPON
    }
}
