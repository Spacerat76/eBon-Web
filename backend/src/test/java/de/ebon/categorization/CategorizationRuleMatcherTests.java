package de.ebon.categorization;

import de.ebon.persistence.model.CategorizationRule;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.RuleMatchField;
import de.ebon.persistence.model.RuleMatchType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategorizationRuleMatcherTests {

    private final CategorizationRuleMatcher matcher = new CategorizationRuleMatcher();

    // Verifies description matching handles normal and compacted OCR/product-name variants.
    @Test
    void matchesDescriptionAcrossContainmentAndCompactNormalization() {
        ReceiptItem item = receiptItem("Bonduelle Country Mix mit Karotte & Goldmais", "REWE");

        assertThat(matcher.matches(rule(RuleMatchField.DESCRIPTION, RuleMatchType.CONTAINS, "country mix"), item))
                .isTrue();
        assertThat(matcher.matches(rule(RuleMatchField.DESCRIPTION, RuleMatchType.CONTAINS, "countrymix"), item))
                .isTrue();
        assertThat(matcher.matches(rule(RuleMatchField.DESCRIPTION, RuleMatchType.STARTS_WITH, "Bonduelle"), item))
                .isTrue();
        assertThat(matcher.matches(rule(RuleMatchField.DESCRIPTION, RuleMatchType.ENDS_WITH, "Goldmais"), item))
                .isTrue();
        assertThat(matcher.matches(rule(RuleMatchField.DESCRIPTION, RuleMatchType.EXACT, "Bonduelle Country Mix mit Karotte & Goldmais"), item))
                .isTrue();
        assertThat(matcher.matches(rule(RuleMatchField.DESCRIPTION, RuleMatchType.CONTAINS, "Schokolade"), item))
                .isFalse();
    }

    // Verifies store-name matching is safe for missing receipts and invalid regex patterns.
    @Test
    void matchesStoreNameAndRejectsInvalidRegexOrMissingTargets() {
        ReceiptItem item = receiptItem("dm-drogerie markt", "dm-drogerie markt");

        assertThat(matcher.matches(rule(RuleMatchField.STORE_NAME, RuleMatchType.CONTAINS, "drogerie"), item))
                .isTrue();
        assertThat(matcher.matches(rule(RuleMatchField.STORE_NAME, RuleMatchType.REGEX, "(unclosed"), item))
                .isFalse();
        assertThat(matcher.matches(rule(RuleMatchField.STORE_NAME, RuleMatchType.CONTAINS, "REWE"), receiptItemWithoutReceipt()))
                .isFalse();
    }

    @Test
    void requiresMatchingStoreWhenDescriptionRuleHasStoreConstraint() {
        CategorizationRule rule = new CategorizationRule(
                new Category("Test", null, null, 1),
                RuleMatchField.DESCRIPTION,
                RuleMatchType.EXACT,
                "BEDIENUNGSTHEKE",
                " REWE ",
                100);

        assertThat(matcher.matches(rule, receiptItem("BEDIENUNGSTHEKE", "rewe"))).isTrue();
        assertThat(matcher.matches(rule, receiptItem("BEDIENUNGSTHEKE", "EDEKA"))).isFalse();
        assertThat(matcher.matches(rule, receiptItemWithoutReceipt())).isFalse();
        assertThat(rule.getStoreName()).isEqualTo("REWE");
    }

    private CategorizationRule rule(RuleMatchField field, RuleMatchType type, String value) {
        return new CategorizationRule(new Category("Test", null, null, 1), field, type, value, 100);
    }

    private ReceiptItem receiptItem(String description, String storeName) {
        Receipt receipt = new Receipt(1, "raw");
        receipt.applyParseResult(null, null, null, null, storeName, null, BigDecimal.ONE, "EUR", null, null, null);
        ReceiptItem item = new ReceiptItem(0, description, BigDecimal.ONE);
        receipt.addItem(item);
        return item;
    }

    private ReceiptItem receiptItemWithoutReceipt() {
        return new ReceiptItem(0, "Unassigned", BigDecimal.ONE);
    }
}
