package de.ebon.parser.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptFormatDefinitionValidatorTests {

    private final ReceiptFormatDefinitionCodec codec = new ReceiptFormatDefinitionCodec();
    private final ReceiptFormatDefinitionValidator validator = new ReceiptFormatDefinitionValidator();
    private final ReceiptTextNormalizer normalizer = new ReceiptTextNormalizer();

    private static final String EXAMPLE = """
            TESTMARKT
            13.07.2026
            ARTIKEL PREIS
            Bio Apfel 1,99
            aus der Region
            SUMME 1,99
            GIROCARD 1,99
            """;

    @Test
    void acceptsVersionOneWithRequiredFieldsAndMultilineDescription() {
        ProfileValidationResult result = validator.validate(validDefinition(), normalizer.normalize(EXAMPLE));
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(codec.read(codec.write(validDefinition()))).isEqualTo(validDefinition());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1})
    void rejectsUnsupportedSchemaVersions(int version) {
        ReceiptFormatDefinition valid = validDefinition();
        assertError(new ReceiptFormatDefinition(version, valid.anchors(), valid.fields(),
                valid.itemRules(), valid.lineRules()), EXAMPLE, ProfileValidationError.Code.SCHEMA_VERSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"schemaVersion\":1,\"script\":\"PRIVATE_SENTINEL\"}",
            "{\"schemaVersion\":1,\"anchors\":[{\"id\":\"a\",\"regex\":\"a\",\"required\":true,\"script\":1}]}",
            "{\"schemaVersion\":1,\"anchors\":[{\"id\":7,\"regex\":\"a\",\"required\":true}]}",
            "{\"schemaVersion\":1,\"anchors\":[{\"id\":\"a\",\"regex\":123,\"required\":true}]}",
            "{\"schemaVersion\":1,\"anchors\":[{\"id\":\"a\",\"regex\":true,\"required\":true}]}",
            "{\"schemaVersion\":1,\"fields\":[{\"field\":\"STORE_NAME\",\"regex\":\"(x)\",\"captureGroup\":1,\"script\":1}]}",
            "{\"schemaVersion\":1,\"fields\":[{\"field\":0,\"regex\":\"(x)\",\"captureGroup\":1}]}",
            "{\"schemaVersion\":1,\"fields\":[{\"field\":\"PASSWORD\",\"regex\":\"(x)\",\"captureGroup\":1}]}",
            "{\"schemaVersion\":1,\"itemRules\":[{\"regex\":\"(x)\",\"captures\":{\"PASSWORD\":1}}]}",
            "{\"schemaVersion\":1,\"itemRules\":[{\"region\":{\"script\":1}}]}",
            "{\"schemaVersion\":1,\"itemRules\":[{\"multiline\":{\"script\":1}}]}",
            "{\"schemaVersion\":1,\"itemRules\":[{\"type\":\"PAYMENT\"}]}",
            "{\"schemaVersion\":1,\"lineRules\":[{\"type\":\"ITEM\",\"regex\":\"x\"}]}",
            "{\"schemaVersion\":1,\"lineRules\":[{\"type\":\"METADATA\",\"regex\":\"x\",\"script\":1}]}",
            "{\"schemaVersion\":\"1\"}",
            "{\"schemaVersion\":1.5}",
            "{\"schemaVersion\":null}",
            "{\"schemaVersion\":1,\"schemaVersion\":2}",
            "{\"schemaVersion\":1} {}",
            "null", "PRIVATE_SENTINEL"
    })
    void rejectsOpenSchemaAndCoercionsWithoutExposingInput(String json) {
        assertThatThrownBy(() -> codec.read(json))
                .isInstanceOf(ProfileDefinitionException.class)
                .satisfies(error -> {
                    ProfileDefinitionException failure = (ProfileDefinitionException) error;
                    assertThat(failure.result().valid()).isFalse();
                    assertThat(failure.result().errors()).extracting(ProfileValidationError::code)
                            .contains(ProfileValidationError.Code.JSON_SCHEMA);
                    assertThat(failure.getCause()).isNull();
                })
                .hasMessageNotContaining("PRIVATE_SENTINEL");
    }

    @Test
    void rejectsMissingRequiredAnchorsAndMissingRequiredFieldRules() {
        assertError(new ReceiptFormatDefinition(1, List.of(), List.of(),
                validDefinition().itemRules(), List.of()), EXAMPLE, ProfileValidationError.Code.REQUIRED_ANCHOR);
        assertError(new ReceiptFormatDefinition(1, validDefinition().anchors(), List.of(),
                validDefinition().itemRules(), List.of()), EXAMPLE, ProfileValidationError.Code.REQUIRED_FIELD);
        assertError(validDefinition(), EXAMPLE.replace("TESTMARKT", "ANDERER MARKT"),
                ProfileValidationError.Code.ANCHOR_NOT_FOUND);
    }

    @Test
    void rejectsDuplicateAnchorsAndUnknownOrReversedRegionBoundaries() {
        ReceiptFormatDefinition valid = validDefinition();
        List<ProfileAnchor> duplicate = new ArrayList<>(valid.anchors());
        duplicate.add(valid.anchors().getFirst());
        assertError(new ReceiptFormatDefinition(1, duplicate, valid.fields(), valid.itemRules(), valid.lineRules()),
                EXAMPLE, ProfileValidationError.Code.DUPLICATE_ANCHOR);
        assertError(withItem(new ProfileItemRule("(.+) ([0-9]+,[0-9]{2})", captures(),
                new ProfileItemRegion("unknown", "total"), null, ProfileItemRule.Type.ITEM)),
                EXAMPLE, ProfileValidationError.Code.REGION_ANCHOR);
        assertError(withItem(new ProfileItemRule("(.+) ([0-9]+,[0-9]{2})", captures(),
                new ProfileItemRegion("total", "items"), null, ProfileItemRule.Type.ITEM)),
                EXAMPLE, ProfileValidationError.Code.REGION_ORDER);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 3})
    void rejectsInvalidItemCaptureReferences(int capture) {
        assertError(withItem(new ProfileItemRule("(.+) ([0-9]+,[0-9]{2})",
                Map.of(ProfileItemRule.Field.DESCRIPTION, 1, ProfileItemRule.Field.TOTAL_PRICE, capture),
                region(), null, ProfileItemRule.Type.ITEM)), EXAMPLE, ProfileValidationError.Code.CAPTURE_GROUP);
    }

    @Test
    void validatesFieldAndContinuationCapturesToo() {
        ReceiptFormatDefinition valid = validDefinition();
        List<ProfileFieldRule> fields = new ArrayList<>(valid.fields());
        fields.add(new ProfileFieldRule(ProfileFieldRule.Field.BONUS_TYPE, "(bonus)", 2, false));
        assertError(new ReceiptFormatDefinition(1, valid.anchors(), fields, valid.itemRules(), valid.lineRules()),
                EXAMPLE, ProfileValidationError.Code.CAPTURE_GROUP);
        assertError(withItem(item(new ProfileMultilineRule("(aus .+)", 2, 2,
                ProfileMultilineRule.Placement.AFTER))), EXAMPLE, ProfileValidationError.Code.CAPTURE_GROUP);
    }

    @Test
    void requiresDescriptionAndTotalPriceCapturesAndNonemptyExampleExtraction() {
        assertError(withItem(new ProfileItemRule("(Bio .+)", Map.of(ProfileItemRule.Field.DESCRIPTION, 1),
                region(), null, ProfileItemRule.Type.ITEM)), EXAMPLE, ProfileValidationError.Code.REQUIRED_CAPTURE);
        assertError(withItem(new ProfileItemRule("(Bio Apfel) (x)?[0-9]+,[0-9]{2}", captures(),
                region(), null, ProfileItemRule.Type.ITEM)), EXAMPLE, ProfileValidationError.Code.EMPTY_CAPTURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[", "(a)\\1", "(?=a)a"})
    void rejectsMalformedAndUnsupportedRegex(String regex) {
        assertError(withItem(new ProfileItemRule(regex, captures(), region(), null, ProfileItemRule.Type.ITEM)),
                EXAMPLE, ProfileValidationError.Code.REGEX_SYNTAX);
    }

    @Test
    void enforcesRegexLengthOnEveryPatternSurface() {
        String tooLong = "a".repeat(1025);
        ReceiptFormatDefinition valid = validDefinition();
        assertError(withItem(new ProfileItemRule(tooLong, captures(), region(), null, ProfileItemRule.Type.ITEM)),
                EXAMPLE, ProfileValidationError.Code.REGEX_LENGTH);
        assertError(new ReceiptFormatDefinition(1, List.of(new ProfileAnchor("a", tooLong, true)),
                valid.fields(), valid.itemRules(), valid.lineRules()), EXAMPLE, ProfileValidationError.Code.REGEX_LENGTH);
        assertError(new ReceiptFormatDefinition(1, valid.anchors(),
                List.of(new ProfileFieldRule(ProfileFieldRule.Field.STORE_NAME, tooLong, 1, true)),
                valid.itemRules(), valid.lineRules()), EXAMPLE, ProfileValidationError.Code.REGEX_LENGTH);
        assertError(withItem(item(new ProfileMultilineRule(tooLong, 1, 2, ProfileMultilineRule.Placement.AFTER))),
                EXAMPLE, ProfileValidationError.Code.REGEX_LENGTH);
        assertError(new ReceiptFormatDefinition(1, valid.anchors(), valid.fields(), valid.itemRules(),
                List.of(new ProfileLineRule(tooLong, ProfileLineRule.Type.METADATA))),
                EXAMPLE, ProfileValidationError.Code.REGEX_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUMME 1,99", "GESAMT 1,99", "ZU ZAHLEN 1,99", "MwSt 19% 1,99", "USt 1,99",
            "TSE 1,99", "Signatur 1,99", "GIROCARD 1,99", "Kartenzahlung 1,99", "BAR 1,99", "VISA 1,99",
            "EC-CASH 1,99", "Rückgeld 1,99"})
    void rejectsEveryItemCollisionEvenAfterAnEarlierSafeMatch(String unsafe) {
        String marker = unsafe.substring(0, unsafe.lastIndexOf(' '));
        assertError(withItem(new ProfileItemRule("^(Bio Apfel|" + java.util.regex.Pattern.quote(marker)
                + ") ([0-9]+,[0-9]{2})$", captures(), region(), null,
                ProfileItemRule.Type.ITEM)), EXAMPLE + unsafe + "\n", ProfileValidationError.Code.ITEM_COLLISION);
    }

    @Test
    void checksWholeSourceLineEvenIfOnlyAmountOrLaterSubstringMatches() {
        assertError(withItem(new ProfileItemRule("(1),(99)", captures(), region(), null, ProfileItemRule.Type.ITEM)),
                EXAMPLE, ProfileValidationError.Code.ITEM_COLLISION);
        assertError(withItem(new ProfileItemRule("(Bio Apfel) (1,99)", captures(), region(), null, ProfileItemRule.Type.ITEM)),
                EXAMPLE + "GIROCARD Bio Apfel 1,99", ProfileValidationError.Code.ITEM_COLLISION);
    }

    @Test
    void checksContinuationAgainstProtectedLinesAndBoundsMerging() {
        assertError(withItem(item(new ProfileMultilineRule("(.+)", 1, 2, ProfileMultilineRule.Placement.AFTER))),
                EXAMPLE, ProfileValidationError.Code.ITEM_COLLISION);
        for (int maxLines : List.of(1, 9)) {
            assertError(withItem(item(new ProfileMultilineRule("(aus .+)", 1, maxLines,
                    ProfileMultilineRule.Placement.AFTER))), EXAMPLE, ProfileValidationError.Code.MULTILINE_LIMIT);
        }
    }

    @Test
    void detectsProfileDeclaredPaymentLinesWithoutRelyingOnKnownWords() {
        ReceiptFormatDefinition valid = validDefinition();
        assertError(new ReceiptFormatDefinition(1, valid.anchors(), valid.fields(),
                List.of(new ProfileItemRule("^(Bio Apfel|Bezahlterminal) ([0-9]+,[0-9]{2})$", captures(), region(), null, ProfileItemRule.Type.ITEM)),
                List.of(new ProfileLineRule("^Bezahlterminal .+", ProfileLineRule.Type.PAYMENT))),
                EXAMPLE + "Bezahlterminal 1,99", ProfileValidationError.Code.ITEM_COLLISION);
    }

    @Test
    void boundsPathologicalRegexEvaluationSynchronouslyWithoutWorkers() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            ProfileRegex pattern = ProfileRegex.compile("(a+)+$");
            assertThat(pattern.findAll("a".repeat(4095) + "!")).isEmpty();
            assertThat(ProfileRegex.compile("(a|aa)+$").findAll("a".repeat(4095) + "!")).isEmpty();
        });
        assertThatThrownBy(() -> ProfileRegex.compile("(a)").findAll("a".repeat(4097)))
                .isInstanceOf(ProfileDefinitionException.class);
        assertThatThrownBy(() -> ProfileRegex.compile("a*"))
                .isInstanceOf(ProfileDefinitionException.class);
        assertThatThrownBy(() -> ProfileRegex.compile("a{1000}b{1000}c{1000}d{1000}e{1000}"))
                .isInstanceOf(ProfileDefinitionException.class);
        ProfileRegex.Budget budget = new ProfileRegex.Budget();
        ProfileRegex large = ProfileRegex.compile("a{1000}z");
        assertThatThrownBy(() -> {
            for (int i = 0; i < 20; i++) {
                large.findAll("a".repeat(4096), budget);
            }
        }).isInstanceOf(ProfileDefinitionException.class);
    }

    @Test
    void regexReturnsAllBoundedCapturesForFutureInterpreter() {
        List<ProfileRegex.Match> matches = ProfileRegex.compile("([A-Z]+) ([0-9]+,[0-9]{2})")
                .findAll("A 1,99 B 2,00");
        assertThat(matches).hasSize(2);
        assertThat(matches.get(1).group(1)).isEqualTo("B");
        assertThat(matches.get(1).group(2)).isEqualTo("2,00");
        assertThat(matches.get(1).start()).isEqualTo(7);
        assertThat(matches.get(1).end()).isEqualTo(13);
    }

    @Test
    void rejectsOversizedDocumentsProfilesAndJsonBeforeMatching() {
        assertError(validDefinition(), "a".repeat(4097), ProfileValidationError.Code.INPUT_LIMIT);
        assertError(validDefinition(), "x\n".repeat(4097), ProfileValidationError.Code.INPUT_LIMIT);
        assertError(validDefinition(), ("x".repeat(1024) + "\n").repeat(257), ProfileValidationError.Code.INPUT_LIMIT);
        ReceiptFormatDefinition valid = validDefinition();
        assertError(new ReceiptFormatDefinition(1, java.util.Collections.nCopies(129,
                new ProfileAnchor("a", "a", true)), valid.fields(), valid.itemRules(), valid.lineRules()),
                EXAMPLE, ProfileValidationError.Code.PROFILE_LIMIT);
        assertThatThrownBy(() -> codec.read(" ".repeat(131073))).isInstanceOf(ProfileDefinitionException.class);
    }

    @Test
    void neverTruncatesExcessMatchesOrPermitsZeroWidthMatches() {
        assertThat(ProfileRegex.compile("a").findAll("a".repeat(64))).hasSize(64);
        assertThatThrownBy(() -> ProfileRegex.compile("a").findAll("a".repeat(65)))
                .isInstanceOf(ProfileDefinitionException.class);
        assertThatThrownBy(() -> ProfileRegex.compile("\\b").findAll("word"))
                .isInstanceOf(ProfileDefinitionException.class);
    }

    @Test
    void rejectsAbsentOptionalCaptureValuesOnEveryMatchedField() {
        ReceiptFormatDefinition valid = validDefinition();
        List<ProfileFieldRule> fields = new ArrayList<>(valid.fields());
        fields.add(new ProfileFieldRule(ProfileFieldRule.Field.BONUS_TYPE, "^Bonus (club)?", 1, false));
        assertError(new ReceiptFormatDefinition(1, valid.anchors(), fields, valid.itemRules(), valid.lineRules()),
                EXAMPLE + "Bonus club\nBonus ", ProfileValidationError.Code.EMPTY_CAPTURE);
    }

    @Test
    void copiesAllCollectionsAndNeverIncludesReceiptTextInValidationErrors() {
        List<ProfileAnchor> anchors = new ArrayList<>(validDefinition().anchors());
        Map<ProfileItemRule.Field, Integer> captures = new HashMap<>(captures());
        ProfileItemRule item = new ProfileItemRule("(x) (y)", captures, region(), null, ProfileItemRule.Type.ITEM);
        ReceiptFormatDefinition definition = new ReceiptFormatDefinition(1, anchors, validDefinition().fields(),
                List.of(item), List.of());
        anchors.clear();
        captures.clear();
        assertThat(definition.anchors()).hasSize(3);
        assertThat(item.captures()).hasSize(2);
        assertThatThrownBy(() -> definition.anchors().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> item.captures().clear()).isInstanceOf(UnsupportedOperationException.class);
        ProfileValidationResult result = validator.validate(definition, normalizer.normalize("PRIVATE_RECEIPT_SENTINEL"));
        assertThat(result.valid()).isFalse();
        assertThat(result.toString()).doesNotContain("PRIVATE_RECEIPT_SENTINEL");
        assertThatThrownBy(() -> result.errors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertError(ReceiptFormatDefinition definition, String example, ProfileValidationError.Code code) {
        ProfileValidationResult result = validator.validate(definition, normalizer.normalize(example));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(ProfileValidationError::code).contains(code);
    }

    private ReceiptFormatDefinition withItem(ProfileItemRule rule) {
        ReceiptFormatDefinition valid = validDefinition();
        return new ReceiptFormatDefinition(1, valid.anchors(), valid.fields(), List.of(rule), valid.lineRules());
    }

    private ReceiptFormatDefinition validDefinition() {
        return new ReceiptFormatDefinition(1,
                List.of(new ProfileAnchor("merchant", "^TESTMARKT$", true),
                        new ProfileAnchor("items", "^ARTIKEL PREIS$", true),
                        new ProfileAnchor("total", "^SUMME ", true)),
                List.of(new ProfileFieldRule(ProfileFieldRule.Field.STORE_NAME, "^(TESTMARKT)$", 1, true),
                        new ProfileFieldRule(ProfileFieldRule.Field.RECEIPT_DATE, "^([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})$", 1, true),
                        new ProfileFieldRule(ProfileFieldRule.Field.TOTAL_AMOUNT, "^SUMME ([0-9]+,[0-9]{2})$", 1, true)),
                List.of(item(new ProfileMultilineRule("^(aus .+)$", 1, 2, ProfileMultilineRule.Placement.AFTER))),
                List.of(new ProfileLineRule("^GIROCARD .+", ProfileLineRule.Type.PAYMENT)));
    }

    private ProfileItemRule item(ProfileMultilineRule multiline) {
        return new ProfileItemRule("^(Bio .+) ([0-9]+,[0-9]{2})$", captures(), region(), multiline, ProfileItemRule.Type.ITEM);
    }

    private ProfileItemRegion region() {
        return new ProfileItemRegion("items", "total");
    }

    private Map<ProfileItemRule.Field, Integer> captures() {
        return Map.of(ProfileItemRule.Field.DESCRIPTION, 1, ProfileItemRule.Field.TOTAL_PRICE, 2);
    }
}
