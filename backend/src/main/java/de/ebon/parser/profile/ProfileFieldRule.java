package de.ebon.parser.profile;

/** Capture groups are numeric and one-based; group zero is not an extraction field. */
public record ProfileFieldRule(Field field, String regex, int captureGroup, boolean required) {
    public enum Field {
        STORE_NAME, STORE_BRANCH, RECEIPT_DATE, RECEIPT_TIME, TOTAL_AMOUNT,
        CURRENCY, BONUS_BALANCE, BONUS_POINTS, BONUS_TYPE
    }
}
