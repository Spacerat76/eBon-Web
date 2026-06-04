package de.ebon.persistence.model;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceModelBehaviorTests {

    // Verifies category updates preserve existing values for null fields and support explicit activation changes.
    @Test
    void categoryUpdateSupportsNullPreserveAndExplicitReplacement() {
        Category category = new Category("Lebensmittel", "#ffffff", "cart", 1);

        category.update(null, null, null, null, null);
        assertThat(category.getName()).isEqualTo("Lebensmittel");
        assertThat(category.getColorHex()).isEqualTo("#ffffff");
        assertThat(category.getIcon()).isEqualTo("cart");
        assertThat(category.getSortOrder()).isEqualTo(1);
        assertThat(category.isActive()).isTrue();

        category.update("Obst und Gemuese", "#00ff00", "leaf", 7, Boolean.FALSE);
        assertThat(category.getName()).isEqualTo("Obst und Gemuese");
        assertThat(category.getColorHex()).isEqualTo("#00ff00");
        assertThat(category.getIcon()).isEqualTo("leaf");
        assertThat(category.getSortOrder()).isEqualTo(7);
        assertThat(category.isActive()).isFalse();

        category.activate();
        assertThat(category.isActive()).isTrue();
        category.deactivate();
        assertThat(category.isActive()).isFalse();
    }

    // Verifies categorization rule updates preserve null fields and support deactivation.
    @Test
    void categorizationRuleUpdateSupportsNullPreserveAndDeactivate() {
        CategorizationRule rule = new CategorizationRule(
                new Category("Lebensmittel", null, null, 1),
                RuleMatchField.DESCRIPTION,
                RuleMatchType.CONTAINS,
                "Milch",
                100);

        rule.update(null, null, null, null, null, null);
        assertThat(rule.getMatchField()).isEqualTo(RuleMatchField.DESCRIPTION);
        assertThat(rule.getMatchType()).isEqualTo(RuleMatchType.CONTAINS);
        assertThat(rule.getMatchValue()).isEqualTo("Milch");
        assertThat(rule.getPriority()).isEqualTo(100);
        assertThat(rule.isActive()).isTrue();

        rule.update(
                new Category("Getraenke", null, null, 2),
                RuleMatchField.STORE_NAME,
                RuleMatchType.STARTS_WITH,
                "REWE",
                75,
                Boolean.FALSE);
        assertThat(rule.getCategory().getName()).isEqualTo("Getraenke");
        assertThat(rule.getMatchField()).isEqualTo(RuleMatchField.STORE_NAME);
        assertThat(rule.getMatchType()).isEqualTo(RuleMatchType.STARTS_WITH);
        assertThat(rule.getMatchValue()).isEqualTo("REWE");
        assertThat(rule.getPriority()).isEqualTo(75);
        assertThat(rule.isActive()).isFalse();

        rule.deactivate();
        assertThat(rule.isActive()).isFalse();
    }

    // Verifies receipt parsing/manual updates keep currency defaults and mark manual edits correctly.
    @Test
    void receiptApplyAndUpdateUseCurrencyFallbackAndManualEditFlags() {
        Receipt receipt = new Receipt(1, "raw");

        receipt.applyParseResult(
                ParseStatus.PARSED,
                null,
                null,
                null,
                "REWE",
                null,
                new BigDecimal("1.00"),
                "   ",
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                "REWE Bonus");
        assertThat(receipt.getCurrency()).isEqualTo("EUR");
        assertThat(receipt.getStoreName()).isEqualTo("REWE");
        assertThat(receipt.getBonusBalance()).isEqualByComparingTo("2.00");
        assertThat(receipt.getBonusPoints()).isEqualByComparingTo("3.00");

        receipt.updateManualValues(
                null,
                null,
                "DM",
                "Am Reuschenberger Markt 3",
                new BigDecimal("2.50"),
                "USD",
                null,
                null,
                null);
        assertThat(receipt.getCurrency()).isEqualTo("USD");
        assertThat(receipt.getStoreName()).isEqualTo("DM");
        assertThat(receipt.getStoreBranch()).isEqualTo("Am Reuschenberger Markt 3");
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo("2.50");
        assertThat(receipt.getParseStatus()).isEqualTo(ParseStatus.MANUALLY_EDITED);
    }

    // Verifies receipt lifecycle defaults initialize timestamps without overwriting preset values.
    @Test
    void receiptPrePersistInitializesAndKeepsExistingTimestamps() throws Exception {
        Receipt fresh = new Receipt(2, "raw");
        fresh.prePersist();
        assertThat(fresh.getImportedAt()).isNotNull();
        assertThat(fresh.getReceiptDate()).isNull();

        Receipt preset = new Receipt(3, "raw");
        OffsetDateTime importedAt = OffsetDateTime.of(2026, 6, 3, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime updatedAt = OffsetDateTime.of(2026, 6, 3, 13, 0, 0, 0, ZoneOffset.UTC);
        setField(preset, "importedAt", importedAt);
        setField(preset, "updatedAt", updatedAt);
        preset.prePersist();
        assertThat(preset.getImportedAt()).isEqualTo(importedAt);
        assertThat(getField(preset, "updatedAt")).isEqualTo(updatedAt);
    }

    // Verifies receipt items enforce category invariants and preserve manual values unless explicitly replaced.
    @Test
    void receiptItemSupportsManualCategoryAndParsedValueUpdates() {
        Category category = new Category("Lebensmittel", null, null, 1);
        Receipt receipt = new Receipt(4, "raw");
        ReceiptItem item = new ReceiptItem(0, "Bio Milch", new BigDecimal("2.49"));
        receipt.addItem(item);

        assertThatThrownBy(() -> item.assignCategory(null, CategorySource.RULE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.assignCategory(category, null))
                .isInstanceOf(IllegalArgumentException.class);

        item.assignCategory(category, CategorySource.RULE);
        assertThat(item.getCategory()).isEqualTo(category);
        assertThat(item.getCategorySource()).isEqualTo(CategorySource.RULE);
        assertThat(item.isManuallyEdited()).isFalse();

        item.assignCategory(category, CategorySource.MANUAL);
        assertThat(item.isManuallyEdited()).isTrue();

        item.clearCategory();
        assertThat(item.getCategory()).isNull();
        assertThat(item.getCategorySource()).isNull();

        item.manuallyClearCategory();
        assertThat(item.getCategory()).isNull();
        assertThat(item.getCategorySource()).isNull();
        assertThat(item.isManuallyEdited()).isTrue();

        item.updateManualValues(
                5,
                "Milch 1l",
                new BigDecimal("1.000"),
                "Stk",
                new BigDecimal("2.49"),
                new BigDecimal("2.49"),
                new BigDecimal("0.00"));
        assertThat(item.getPositionIndex()).isEqualTo(5);
        assertThat(item.getDescription()).isEqualTo("Milch 1l");
        assertThat(item.getQuantity()).isEqualByComparingTo("1.000");
        assertThat(item.getUnit()).isEqualTo("Stk");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("2.49");
        assertThat(item.getTotalPrice()).isEqualByComparingTo("2.49");
        assertThat(item.getDiscountAmount()).isEqualByComparingTo("0.00");

        item.updateManualValues(null, null, null, null, null, null, null);
        assertThat(item.getPositionIndex()).isEqualTo(5);
        assertThat(item.getDescription()).isEqualTo("Milch 1l");
        assertThat(item.getQuantity()).isEqualByComparingTo("1.000");
        assertThat(item.getUnit()).isEqualTo("Stk");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("2.49");
        assertThat(item.getTotalPrice()).isEqualByComparingTo("2.49");
        assertThat(item.getDiscountAmount()).isEqualByComparingTo("0.00");

        item.updateParsedValues(new BigDecimal("2.000"), "kg", new BigDecimal("1.25"), new BigDecimal("0.10"));
        assertThat(item.getQuantity()).isEqualByComparingTo("2.000");
        assertThat(item.getUnit()).isEqualTo("kg");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("1.25");
        assertThat(item.getDiscountAmount()).isEqualByComparingTo("0.10");
    }

    // Verifies parse-rule lifecycle defaults initialize timestamps without overwriting preset values.
    @Test
    void parseRulePrePersistInitializesCreatedAtAndKeepsPresetValues() throws Exception {
        ParseRule fresh = new ParseRule("REWE", ParseRuleType.DATE_PATTERN, "\\d+", "date", RuleSource.MANUAL);
        fresh.prePersist();
        assertThat(getField(fresh, "createdAt")).isNotNull();

        ParseRule preset = new ParseRule("DM", ParseRuleType.TOTAL_PATTERN, "sum", null, RuleSource.AI_ADAPTED);
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 6, 3, 14, 0, 0, 0, ZoneOffset.UTC);
        setField(preset, "createdAt", createdAt);
        preset.prePersist();
        assertThat(getField(preset, "createdAt")).isEqualTo(createdAt);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
