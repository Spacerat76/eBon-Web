package de.ebon.parser;

import de.ebon.persistence.model.ParseRuleType;
import de.ebon.persistence.model.ParseRuleValidationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParseRuleSuggestionValidatorTests {

    private final ParseRuleSuggestionValidator validator = new ParseRuleSuggestionValidator();

    // Verifies valid named capture groups are accepted for a real item-like example line.
    @Test
    void validRegexWithExpectedGroupsIsAccepted() {
        ParseRuleSuggestionValidator.ValidationResult result = validator.validate(
                "BIO MILCH 1,29",
                ParseRuleType.ITEM_PATTERN,
                "^(?<description>.+?)\\s+(?<total>\\d+,\\d{2})$",
                "description,total");

        assertThat(result.status()).isEqualTo(ParseRuleValidationStatus.VALID);
    }

    // Verifies syntactically invalid regexes are blocked before a user can accept them.
    @Test
    void invalidRegexIsRejected() {
        ParseRuleSuggestionValidator.ValidationResult result = validator.validate(
                "BIO MILCH 1,29",
                ParseRuleType.ITEM_PATTERN,
                "[",
                "description");

        assertThat(result.status()).isEqualTo(ParseRuleValidationStatus.INVALID_REGEX);
    }

    // Verifies rules that match only tax/payment noise are flagged as collision risks.
    @Test
    void taxAndPaymentLinesAreCollisionRiskForItemRules() {
        ParseRuleSuggestionValidator.ValidationResult result = validator.validate(
                "TSE Signatur 123\nSUMME 2,50",
                ParseRuleType.ITEM_PATTERN,
                "^SUMME\\s+(?<total>\\d+,\\d{2})$",
                "total");

        assertThat(result.status()).isEqualTo(ParseRuleValidationStatus.COLLISION_RISK);
    }
}
