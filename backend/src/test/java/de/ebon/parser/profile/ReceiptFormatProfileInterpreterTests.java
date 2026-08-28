package de.ebon.parser.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ebon.parser.ParsedReceipt;
import de.ebon.parser.ParsedReceiptItem;
import de.ebon.persistence.model.ParseLineType;
import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.ParseStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReceiptFormatProfileInterpreterTests {
    private final ReceiptTextNormalizer normalizer = new ReceiptTextNormalizer();
    private final ReceiptFormatProfileInterpreter interpreter = new ReceiptFormatProfileInterpreter();
    private final ProfileParseQualityGate gate = new ProfileParseQualityGate();
    private final ReceiptFormatDefinitionCodec codec = new ReceiptFormatDefinitionCodec();

    static Stream<String> corpus() {
        return Stream.of("format_profile_multiline", "format_profile_unresolved", "format_profile_monetary_remainder");
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void interpretsSyntheticCorpusWithExactlyOneTracePerSourceLine(String name) throws IOException {
        String text = resource(name + ".txt");
        ReceiptFormatDefinition definition = codec.read(resource(name + ".profile.json"));
        assertThat(new ReceiptFormatDefinitionValidator().validate(definition, normalizer.normalize(text)).valid()).isTrue();
        JsonNode expected = JsonMapper.builder().build().readTree(resource(name + ".expected.json"));
        ProfileInterpretationResult result = interpreter.interpret(definition, normalizer.normalize(text));
        ProfileParseOutcome outcome = gate.validate(result);
        assertThat(outcome.parseResult().parseStatus().name()).isEqualTo(expected.get("parseStatus").asString());
        assertThat(outcome.parseResult().parseSource()).isEqualTo(ParseSource.RULE);
        ParsedReceipt receipt = outcome.parseResult().receipt();
        assertThat(receipt.storeName()).isEqualTo(expected.get("storeName").asString());
        assertThat(receipt.receiptDate().toString()).isEqualTo(expected.get("receiptDate").asString());
        assertThat(receipt.totalAmount()).isEqualByComparingTo(expected.get("totalAmount").asString());
        assertThat(receipt.items()).hasSize(expected.get("items").size());
        for (int i = 0; i < receipt.items().size(); i++) {
            ParsedReceiptItem item = receipt.items().get(i);
            JsonNode expectedItem = expected.get("items").get(i);
            assertThat(item.positionIndex()).isEqualTo(expectedItem.get("positionIndex").asInt());
            assertThat(item.description()).isEqualTo(expectedItem.get("description").asString());
            assertThat(item.totalPrice()).isEqualByComparingTo(expectedItem.get("totalPrice").asString());
        }
        assertThat(result.traces()).extracting(ParsedLineTrace::lineNumber).doesNotHaveDuplicates();
        assertThat(result.traces()).hasSize(normalizer.normalize(text).lines().size());
        for (int i = 0; i < result.traces().size(); i++) {
            ParsedLineTrace trace = result.traces().get(i);
            assertThat(trace.lineNumber()).isEqualTo(i + 1);
            assertThat(trace.lineType().name()).isEqualTo(expected.get("lineTypes").get(i).asString());
            JsonNode index = expected.get("positionIndices").get(i);
            assertThat(trace.positionIndex()).isEqualTo(index.isNull() ? null : index.asInt());
            NormalizedReceiptLine source = normalizer.normalize(text).lines().get(i);
            assertThat(trace.startOffset()).isEqualTo(source.startOffset());
            assertThat(trace.endOffset()).isEqualTo(source.endOffset());
            assertThat(trace.reason()).isNotBlank();
        }
        assertThat(result.traces().get(3).extractedFields()).containsEntry("DESCRIPTION", "Bio Apfel");
        assertThatThrownBy(() -> outcome.traces().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.traces().get(3).extractedFields().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource({"1.99,PARSED", "2.01,PARSED", "1.97,PARSED", "2.02,PARSE_ERROR", "1.96,PARSE_ERROR"})
    void preservesExactSumToleranceForCompleteCoverage(String total, ParseStatus expected) throws IOException {
        assertThat(parse(baseText().replace("SUMME 1,99", "SUMME " + total)).parseResult().parseStatus()).isEqualTo(expected);
    }

    @Test
    void unknownPriceIsNeverImplicitlyIgnoredEvenWhenKnownItemsAlreadyReconcile() throws IOException {
        ProfileParseOutcome outcome = parse(baseText().replace("SUMME", "Extra 0,00\nSUMME"));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(outcome.parseResult().receipt().items()).hasSize(1);
        assertThat(outcome.traces().get(4).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bio Apfel 1,99 Pfand 0,25 Gutschein -0,25", "Bio Apfel 1,99 Extra 0,00",
            "Pfand 0,25 Gutschein -0,25 Bio Apfel 1,99", "Extra 0,00 Bio Apfel 1,99"})
    void partialItemMatchCannotHideUnconsumedMonetaryPrefixOrSuffix(String line) throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule original = d.itemRules().getFirst();
        ProfileItemRule partial = new ProfileItemRule("(Bio Apfel) ([0-9]+,[0-9]{2})", original.captures(),
                original.region(), null, original.type());
        ReceiptFormatDefinition profile = withItems(d, List.of(partial));
        String text = baseText().replace("Bio Apfel 1,99", line);
        assertThat(new ReceiptFormatDefinitionValidator().validate(profile, normalizer.normalize(text)).valid()).isTrue();
        ProfileParseOutcome outcome = interpret(profile, text);
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(outcome.parseResult().receipt().items()).isEmpty();
        assertThat(outcome.traces().get(3).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
        assertThat(outcome.traces().get(3).positionIndex()).isNull();
        assertThat(outcome.traces()).extracting(ParsedLineTrace::lineNumber).containsExactly(1, 2, 3, 4, 5);

        ProfileParseOutcome withKnownItem = interpret(profile, text.replace("ARTIKEL PREIS\n", "ARTIKEL PREIS\nBio Apfel 0,00\n"));
        assertThat(withKnownItem.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(withKnownItem.parseResult().receipt().items()).hasSize(1);
        assertThat(withKnownItem.parseResult().receipt().items().getFirst().totalPrice()).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bio Apfel 1,99", "** Bio Apfel 1,99 **"})
    void partialMatchWithNoMonetaryRemainderStillResolvesPosition(String line) throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule original = d.itemRules().getFirst();
        ProfileItemRule partial = new ProfileItemRule("(Bio Apfel) ([0-9]+,[0-9]{2})", original.captures(),
                original.region(), null, original.type());
        ProfileParseOutcome outcome = interpret(withItems(d, List.of(partial)), baseText().replace("Bio Apfel 1,99", line));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(outcome.parseResult().receipt().items()).hasSize(1);
        assertThat(outcome.traces().get(3).lineType()).isEqualTo(ParseLineType.POSITION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"^(Bio Apfel) ([0-9]+,[0-9]{2}).*$", "^.*(Bio Apfel) ([0-9]+,[0-9]{2}).*$"})
    void fullWildcardMatchDoesNotReplaceSemanticCaptureCoverage(String regex) throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule original = d.itemRules().getFirst();
        ProfileItemRule partial = new ProfileItemRule(regex, original.captures(), original.region(), null, original.type());
        String text = baseText().replace("Bio Apfel 1,99", "Bio Apfel 1,99 Pfand 0,25 Gutschein -0,25");
        ReceiptFormatDefinition profile = withItems(d, List.of(partial));
        assertThat(new ReceiptFormatDefinitionValidator().validate(profile, normalizer.normalize(text)).valid()).isTrue();
        ProfileParseOutcome outcome = interpret(profile, text);
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(outcome.parseResult().receipt().items()).isEmpty();
        assertThat(outcome.traces().get(3).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    @Test
    void partialNumberCaptureCannotTreatTheLastDigitAsHarmlessRemainder() throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule original = d.itemRules().getFirst();
        ProfileItemRule partial = new ProfileItemRule("^(Bio Apfel) ([0-9]+,[0-9]).*$", original.captures(),
                original.region(), null, original.type());
        ProfileParseOutcome outcome = interpret(withItems(d, List.of(partial)),
                baseText().replace("Bio Apfel 1,99", "Bio Apfel 1,90").replace("SUMME 1,99", "SUMME 1,90"));
        assertThat(outcome.parseResult().receipt().items()).isEmpty();
        assertThat(outcome.traces().get(3).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    @Test
    void twoDecimalCaptureCannotCoverAThreeDecimalSourceAmount() throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule original = d.itemRules().getFirst();
        ProfileItemRule partial = new ProfileItemRule("^(Bio Apfel) ([0-9]+,[0-9]{2}).*$", original.captures(),
                original.region(), null, original.type());
        ProfileParseOutcome outcome = interpret(withItems(d, List.of(partial)), baseText().replace("Bio Apfel 1,99", "Bio Apfel 1,999"));
        assertThat(outcome.parseResult().receipt().items()).isEmpty();
        assertThat(outcome.traces().get(3).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "€"})
    void fullyCapturedIntegerPricesDoNotNeedToCaptureTheCurrencySuffix(String currency) throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule original = d.itemRules().getFirst();
        ProfileItemRule integer = new ProfileItemRule("^(Bio Apfel) ([0-9]+) (?:EUR|€)$", original.captures(),
                original.region(), null, original.type());
        List<ProfileFieldRule> fields = new ArrayList<>(d.fields());
        fields.set(2, new ProfileFieldRule(ProfileFieldRule.Field.TOTAL_AMOUNT, "^SUMME ([0-9]+) (?:EUR|€)$", 1, true));
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), fields, List.of(integer), d.lineRules());
        String text = baseText().replace("1,99", "2 " + currency);
        assertThat(new ReceiptFormatDefinitionValidator().validate(profile, normalizer.normalize(text)).valid()).isTrue();
        ProfileParseOutcome outcome = interpret(profile, text);
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(outcome.parseResult().receipt().items().getFirst().totalPrice()).isEqualByComparingTo("2");
        assertThat(outcome.traces().get(3).lineType()).isEqualTo(ParseLineType.POSITION);
        assertThat(outcome.traces().get(4).lineType()).isEqualTo(ParseLineType.TOTAL);
    }

    @Test
    void fullWildcardFieldMatchCannotHideAmountsOutsideMappedCapture() throws IOException {
        ReceiptFormatDefinition d = definition();
        List<ProfileFieldRule> fields = new ArrayList<>(d.fields());
        fields.set(2, new ProfileFieldRule(ProfileFieldRule.Field.TOTAL_AMOUNT, "^SUMME ([0-9]+,[0-9]{2}).*$", 1, true));
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), fields, d.itemRules(), d.lineRules());
        ProfileParseOutcome outcome = interpret(profile, baseText().replace("SUMME 1,99", "SUMME 1,99 Extra 0,00"));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(outcome.traces().getLast().lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    @Test
    void anchorAloneCannotDeclareAnUncapturedAmountAsMetadata() throws IOException {
        ReceiptFormatDefinition d = definition();
        List<ProfileAnchor> anchors = new ArrayList<>(d.anchors());
        anchors.set(1, new ProfileAnchor("start", "^ARTIKEL PREIS.*$", true));
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, anchors, d.fields(), d.itemRules(), d.lineRules());
        ProfileParseOutcome outcome = interpret(profile, baseText().replace("ARTIKEL PREIS", "ARTIKEL PREIS Extra 0,00"));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(outcome.traces().get(2).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    @Test
    void partialTotalFieldCannotHideMonetaryRemainderBehindAFullAnchor() throws IOException {
        ReceiptFormatDefinition d = definition();
        List<ProfileFieldRule> fields = new ArrayList<>(d.fields());
        fields.set(2, new ProfileFieldRule(ProfileFieldRule.Field.TOTAL_AMOUNT, "^SUMME ([0-9]+,[0-9]{2})", 1, true));
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), fields, d.itemRules(), d.lineRules());
        ProfileParseOutcome outcome = interpret(profile, baseText().replace("SUMME 1,99", "SUMME 1,99 Extra 0,00"));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(outcome.traces().getLast().lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GIROCARD 1,99 Extra 0,00", "Extra 0,00 GIROCARD 1,99"})
    void partialExplicitLineRuleCannotHideMonetaryRemainder(String line) throws IOException {
        ReceiptFormatDefinition d = definition();
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), d.fields(), d.itemRules(),
                List.of(new ProfileLineRule("GIROCARD [0-9]+,[0-9]{2}", ProfileLineRule.Type.PAYMENT)));
        ProfileParseOutcome outcome = interpret(profile, baseText() + line + "\n");
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(outcome.traces().getLast().lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Total Repair Shampoo 0,00|1,99", "Total Repair Shampoo 0,50|2,49",
            "Girocard Huelle 0,00|1,99", "Girocard Huelle 0,50|2,49",
            "MwSt Ratgeber 0,00|1,99", "MwSt Ratgeber 0,50|2,49",
            "TSE Handbuch 0,00|1,99", "TSE Handbuch 0,50|2,49"
    })
    void lexicalKeywordsCannotResolveUnknownPriceLinesWithoutExplicitEvidence(String unknown, String total) throws IOException {
        ReceiptFormatDefinition profile = definition();
        assertThat(new ReceiptFormatDefinitionValidator().validate(profile, normalizer.normalize(baseText())).valid()).isTrue();
        String receipt = baseText().replace("SUMME 1,99", unknown + "\nSUMME " + total);
        ProfileParseOutcome outcome = interpret(profile, receipt);
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(outcome.parseResult().receipt().items()).hasSize(1);
        assertThat(outcome.parseResult().receipt().items().getFirst().description()).isEqualTo("Bio Apfel");
        assertThat(outcome.traces().get(4).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
        assertThat(outcome.traces().get(4).positionIndex()).isNull();
        assertThat(outcome.traces().get(4).extractedFields()).isEmpty();
    }

    @Test
    void explicitValidatedFooterRulesAndTotalFieldStillResolveGenuineFooterLines() throws IOException {
        ReceiptFormatDefinition d = definition();
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), d.fields(), d.itemRules(), List.of(
                new ProfileLineRule("^GIROCARD [0-9,]+$", ProfileLineRule.Type.PAYMENT),
                new ProfileLineRule("^MWST [0-9,]+$", ProfileLineRule.Type.TAX),
                new ProfileLineRule("^TSE [0-9]+$", ProfileLineRule.Type.TSE)));
        String receipt = baseText() + "GIROCARD 1,99\nMWST 0,14\nTSE 12345\n";
        assertThat(new ReceiptFormatDefinitionValidator().validate(profile, normalizer.normalize(receipt)).valid()).isTrue();
        ProfileParseOutcome outcome = interpret(profile, receipt);
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(outcome.traces().subList(4, 8)).extracting(ParsedLineTrace::lineType)
                .containsExactly(ParseLineType.TOTAL, ParseLineType.PAYMENT, ParseLineType.TAX, ParseLineType.METADATA);
        assertThat(outcome.traces().get(4).extractedFields()).containsEntry("TOTAL_AMOUNT", "1,99");
        assertThat(outcome.parseResult().receipt().items()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"31.02.2026", "00.07.2026", "99.99.9999"})
    void malformedRequiredDateOverridesIncompleteCoverage(String date) throws IOException {
        ProfileParseOutcome outcome = parse(baseText().replace("13.07.2026", date).replace("SUMME", "Extra 0,50\nSUMME"));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(outcome.parseResult().receipt().items()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,2,3", "1.23.4,56", "NaN", "1e3", "--1,99"})
    void rejectsMalformedNumbersWithoutDiscardingOtherFields(String amount) throws IOException {
        ProfileParseOutcome total = parse(baseText().replace("SUMME 1,99", "SUMME " + amount));
        assertThat(total.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(total.parseResult().receipt().items()).hasSize(1);
        ProfileParseOutcome item = parse(baseText().replace("Bio Apfel 1,99", "Bio Apfel " + amount));
        assertThat(item.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(item.parseResult().receipt().items()).isEmpty();
        assertThat(item.parseResult().receipt().totalAmount()).isEqualByComparingTo("1.99");
    }

    @Test
    void normalizesGermanThousandsAndPreservesOptionalItemFields() throws IOException {
        ReceiptFormatDefinition definition = definition();
        ProfileItemRule item = new ProfileItemRule("^(Bio Apfel) ([0-9,]+) (kg) ([0-9.,]+) ([0-9.,]+) (-[0-9,]+)$",
                Map.of(ProfileItemRule.Field.DESCRIPTION, 1, ProfileItemRule.Field.QUANTITY, 2,
                        ProfileItemRule.Field.UNIT, 3, ProfileItemRule.Field.UNIT_PRICE, 4,
                        ProfileItemRule.Field.TOTAL_PRICE, 5, ProfileItemRule.Field.DISCOUNT_AMOUNT, 6),
                definition.itemRules().getFirst().region(), null, ProfileItemRule.Type.ITEM);
        String text = baseText().replace("Bio Apfel 1,99", "Bio Apfel 2,5 kg 493,824 1.234,56 -0,10").replace("SUMME 1,99", "SUMME 1.234,56");
        ProfileParseOutcome outcome = interpret(withItems(definition, List.of(item)), text);
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(outcome.parseResult().receipt().items().getFirst()).isEqualTo(new ParsedReceiptItem(
                0, "Bio Apfel", new BigDecimal("2.5"), "kg", new BigDecimal("493.824"), new BigDecimal("1234.56"), new BigDecimal("-0.10")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TESTMARKT", "ARTIKEL PREIS", "SUMME 1,99"})
    void missingRequiredAnchorIsErrorWithCompleteRemainingTraces(String missing) throws IOException {
        ProfileParseOutcome outcome = parse(baseText().replace(missing + "\n", ""));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(outcome.traces()).hasSize(4);
    }

    @Test
    void duplicateAnchorAndConflictingFieldMatchesCannotBecomeParsed() throws IOException {
        assertThat(parse(baseText() + "SUMME 1,99\n").parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(parse(baseText() + "14.07.2026\n").parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
    }

    @ParameterizedTest
    @CsvSource({"MWST,TAX", "GIROCARD,PAYMENT", "TSE,METADATA", "SUMME,TOTAL"})
    void protectsNewTaxPaymentTseAndTotalLinesFromBroadItemPatterns(String label, ParseLineType type) throws IOException {
        ReceiptFormatDefinition definition = definition();
        ProfileItemRule broad = new ProfileItemRule("^(.+) ([0-9,]+)$", definition.itemRules().getFirst().captures(),
                definition.itemRules().getFirst().region(), null, ProfileItemRule.Type.ITEM);
        // Profile is validated against a safe example, then sees a different receipt at interpretation time.
        ReceiptFormatDefinition profile = withItems(definition, List.of(broad));
        assertThat(new ReceiptFormatDefinitionValidator().validate(profile, normalizer.normalize(baseText()
                .replace("SUMME 1,99", "SUMME 1.99"))).valid()).isTrue();
        String labelLine = label.equals("SUMME") ? "TOTAL" : label;
        ProfileParseOutcome outcome = interpret(profile, baseText().replace("SUMME 1,99", labelLine + " 0,50\nSUMME 1,99"));
        assertThat(outcome.parseResult().receipt().items()).hasSize(1);
        assertThat(outcome.traces().get(4).positionIndex()).isNull();
        assertThat(outcome.traces().get(4).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
        assertThat(outcome.parseResult().parseStatus()).isNotEqualTo(ParseStatus.PARSED);
    }

    @Test
    void overlappingItemRulesDoNotCreateDuplicateOrArbitrarilyTrustedItems() throws IOException {
        ReceiptFormatDefinition definition = definition();
        ProfileParseOutcome outcome = interpret(withItems(definition,
                List.of(definition.itemRules().getFirst(), definition.itemRules().getFirst())), baseText());
        assertThat(outcome.parseResult().receipt().items()).isEmpty();
        assertThat(outcome.traces().get(3).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
    }

    @Test
    void explicitSafeIgnoreCanCoverAPriceButCannotHideAnItemCollision() throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileLineRule ignore = new ProfileLineRule("^Extra 0,50$", ProfileLineRule.Type.IGNORED_SAFE);
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), d.fields(), d.itemRules(), List.of(ignore));
        assertThat(interpret(profile, baseText().replace("SUMME", "Extra 0,50\nSUMME")).parseResult().parseStatus()).isEqualTo(ParseStatus.PARSED);
        ProfileLineRule collision = new ProfileLineRule("^Bio Apfel .+$", ProfileLineRule.Type.PAYMENT);
        profile = new ReceiptFormatDefinition(1, d.anchors(), d.fields(), d.itemRules(), List.of(collision));
        assertThat(interpret(profile, baseText()).parseResult().receipt().items()).isEmpty();
    }

    @Test
    void beforeMultilineMergesInDocumentOrderAndCannotCrossBlankPhysicalLine() throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule rule = d.itemRules().getFirst();
        ProfileItemRule before = new ProfileItemRule(rule.regex(), rule.captures(), rule.region(),
                new ProfileMultilineRule("^(Region|Bio)$", 1, 3, ProfileMultilineRule.Placement.BEFORE), rule.type());
        ReceiptFormatDefinition profile = withItems(d, List.of(before));
        ProfileParseOutcome contiguous = interpret(profile, baseText().replace("Bio Apfel", "Region\nBio\nBio Apfel"));
        assertThat(contiguous.parseResult().receipt().items().getFirst().description()).isEqualTo("Region Bio Bio Apfel");
        assertThat(contiguous.traces().subList(3, 6)).extracting(ParsedLineTrace::positionIndex).containsOnly(0);
        ProfileParseOutcome blank = interpret(profile, baseText().replace("Bio Apfel", "Region\n\nBio Apfel"));
        assertThat(blank.parseResult().receipt().items().getFirst().description()).isEqualTo("Bio Apfel");
        assertThat(blank.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(blank.traces()).extracting(ParsedLineTrace::lineNumber).containsExactly(1, 2, 3, 4, 6, 7);
    }

    @Test
    void sharedMultilineSourceIsNotAssignedToTwoItems() throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule rule = d.itemRules().getFirst();
        ProfileItemRule first = new ProfileItemRule(rule.regex(), rule.captures(), rule.region(),
                new ProfileMultilineRule("^(Beschreibung)$", 1, 2, ProfileMultilineRule.Placement.AFTER), rule.type());
        ProfileItemRule second = new ProfileItemRule("^(Birne) (.+)$", rule.captures(), rule.region(),
                new ProfileMultilineRule("^(Beschreibung)$", 1, 2, ProfileMultilineRule.Placement.BEFORE), rule.type());
        ProfileParseOutcome outcome = interpret(withItems(d, List.of(first, second)),
                baseText().replace("SUMME", "Beschreibung\nBirne 0,50\nSUMME"));
        assertThat(outcome.parseResult().receipt().items()).isEmpty();
        assertThat(outcome.traces().subList(3, 6)).extracting(ParsedLineTrace::lineType).containsOnly(ParseLineType.UNRESOLVED);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1, 3})
    void qualityGateRejectsInvalidIndicesEvenWithUnresolvedCoverage(int index) throws IOException {
        ProfileInterpretationResult valid = interpreter.interpret(definition(), normalizer.normalize(baseText() + "Extra 0,50\n"));
        ParsedReceipt r = valid.receipt();
        ParsedReceiptItem item = r.items().getFirst();
        ParsedReceipt invalid = new ParsedReceipt(r.receiptDate(), r.receiptTime(), r.storeName(), r.storeBranch(), r.totalAmount(),
                r.currency(), r.bonusBalance(), r.bonusPoints(), r.bonusType(), List.of(new ParsedReceiptItem(index,
                item.description(), item.quantity(), item.unit(), item.unitPrice(), item.totalPrice(), item.discountAmount())));
        assertThat(gate.validate(new ProfileInterpretationResult(invalid, valid.traces(), List.of())).parseResult().parseStatus())
                .isEqualTo(ParseStatus.PARSE_ERROR);
    }

    @Test
    void qualityGateRejectsDuplicateIndicesAndDuplicateTraceLines() throws IOException {
        ProfileInterpretationResult valid = interpreter.interpret(definition(), normalizer.normalize(baseText()));
        ParsedReceipt r = valid.receipt();
        ParsedReceipt duplicate = new ParsedReceipt(r.receiptDate(), null, r.storeName(), null, new BigDecimal("3.98"),
                "EUR", null, null, null, List.of(r.items().getFirst(), r.items().getFirst()));
        assertThat(gate.validate(new ProfileInterpretationResult(duplicate, valid.traces(), List.of())).parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        List<ParsedLineTrace> traces = new ArrayList<>(valid.traces());
        traces.add(traces.getFirst());
        assertThat(gate.validate(new ProfileInterpretationResult(r, traces, List.of())).parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
    }

    @Test
    void interpreterEnforcesWholeDocumentAndSharedEvaluationBounds() throws IOException {
        String tooLong = baseText() + "x".repeat(ProfileRegex.MAX_LINE_LENGTH + 1);
        assertThat(parse(tooLong).parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        ReceiptFormatDefinition d = definition();
        List<ProfileLineRule> expensive = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            expensive.add(new ProfileLineRule("^" + "x".repeat(200) + i + "$", ProfileLineRule.Type.METADATA));
        }
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), d.fields(), d.itemRules(), expensive);
        ProfileParseOutcome outcome = interpret(profile, baseText() + "x".repeat(4096) + "\n" + "x".repeat(4096));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(outcome.parseResult().errorMessage()).contains("EVALUATION_LIMIT");
        assertThat(outcome.traces()).hasSize(7);
    }

    @Test
    void extractsOptionalFieldsWithoutConflictingWithTotalOnTheSameSourceLine() throws IOException {
        ReceiptFormatDefinition d = definition();
        List<ProfileFieldRule> fields = new ArrayList<>(d.fields());
        fields.set(2, new ProfileFieldRule(ProfileFieldRule.Field.TOTAL_AMOUNT, "^SUMME ([0-9,]+) EUR$", 1, true));
        fields.add(new ProfileFieldRule(ProfileFieldRule.Field.CURRENCY, "^SUMME [0-9,]+ (EUR)$", 1, false));
        fields.add(new ProfileFieldRule(ProfileFieldRule.Field.RECEIPT_TIME, "^ZEIT (.+)$", 1, false));
        fields.add(new ProfileFieldRule(ProfileFieldRule.Field.STORE_BRANCH, "^FILIALE (.+)$", 1, true));
        fields.add(new ProfileFieldRule(ProfileFieldRule.Field.BONUS_BALANCE, "^NEU GUTHABEN (.+)$", 1, false));
        fields.add(new ProfileFieldRule(ProfileFieldRule.Field.BONUS_POINTS, "^NEU PUNKTE (.+)$", 1, false));
        fields.add(new ProfileFieldRule(ProfileFieldRule.Field.BONUS_TYPE, "^PROGRAMM (.+)$", 1, false));
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), fields, d.itemRules(), d.lineRules());
        String text = baseText().replace("SUMME 1,99", "SUMME 1,99 EUR")
                + "ZEIT 12:34:56\nFILIALE Nord\nNEU GUTHABEN 0,20\nNEU PUNKTE 2\nPROGRAMM Testbonus\n";
        ProfileParseOutcome outcome = interpret(profile, text);
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSED);
        ParsedReceipt receipt = outcome.parseResult().receipt();
        assertThat(receipt.receiptTime().toString()).isEqualTo("12:34:56");
        assertThat(receipt.storeBranch()).isEqualTo("Nord");
        assertThat(receipt.currency()).isEqualTo("EUR");
        assertThat(receipt.bonusBalance()).isEqualByComparingTo("0.20");
        assertThat(receipt.bonusPoints()).isEqualByComparingTo("2");
        assertThat(receipt.bonusType()).isEqualTo("Testbonus");
        assertThat(outcome.traces().get(4).lineType()).isEqualTo(ParseLineType.TOTAL);
        assertThat(interpret(profile, text.replace("ZEIT 12:34:56", "ZEIT 25:00"))
                .parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(interpret(profile, text.replace("FILIALE Nord\n", ""))
                .parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
    }

    @Test
    void missingDateAndInvalidKnownItemOverrideReviewButPreserveValidItems() throws IOException {
        ProfileParseOutcome missing = parse(baseText().replace("13.07.2026\n", "") + "Extra 0,50\n");
        assertThat(missing.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(missing.parseResult().receipt().items()).hasSize(1);
        ProfileParseOutcome malformed = parse(baseText().replace("SUMME", "Bio Apfel NaN\nExtra 0,50\nSUMME"));
        assertThat(malformed.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(malformed.parseResult().receipt().items()).hasSize(1);
        assertThat(malformed.parseResult().receipt().items().getFirst().positionIndex()).isZero();
    }

    @Test
    void multilineLimitDoesNotConsumeExcessOrCrossRegionBoundaries() throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule rule = d.itemRules().getFirst();
        ProfileItemRule after = new ProfileItemRule(rule.regex(), rule.captures(), rule.region(),
                new ProfileMultilineRule("^(Zusatz|SUMME .+)$", 1, 2, ProfileMultilineRule.Placement.AFTER), rule.type());
        ReceiptFormatDefinition profile = withItems(d, List.of(after));
        ProfileParseOutcome limited = interpret(profile, baseText().replace("SUMME", "Zusatz\nZusatz\nSUMME"));
        assertThat(limited.parseResult().receipt().items().getFirst().description()).isEqualTo("Bio Apfel Zusatz");
        assertThat(limited.traces().get(5).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
        assertThat(limited.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        ProfileParseOutcome boundary = interpret(profile, baseText());
        assertThat(boundary.parseResult().receipt().items().getFirst().description()).isEqualTo("Bio Apfel");
        assertThat(boundary.traces().getLast().lineType()).isEqualTo(ParseLineType.TOTAL);
    }

    @Test
    void conflictingClassifiersAndMissingPositionEvidenceCannotBeTrusted() throws IOException {
        ReceiptFormatDefinition d = definition();
        ReceiptFormatDefinition profile = new ReceiptFormatDefinition(1, d.anchors(), d.fields(), d.itemRules(), List.of(
                new ProfileLineRule("^Extra 0,50$", ProfileLineRule.Type.TAX),
                new ProfileLineRule("^Extra 0,50$", ProfileLineRule.Type.PAYMENT)));
        ProfileParseOutcome outcome = interpret(profile, baseText() + "Extra 0,50\n");
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(outcome.traces().getLast().lineType()).isEqualTo(ParseLineType.UNRESOLVED);
        ProfileInterpretationResult valid = interpreter.interpret(d, normalizer.normalize(baseText()));
        assertThat(gate.validate(new ProfileInterpretationResult(valid.receipt(), List.of(), List.of()))
                .parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_ERROR);
    }

    @Test
    void multilineDescriptionCannotSwallowAnUnknownPriceBearingLine() throws IOException {
        ReceiptFormatDefinition d = definition();
        ProfileItemRule rule = d.itemRules().getFirst();
        ProfileItemRule after = new ProfileItemRule(rule.regex(), rule.captures(), rule.region(),
                new ProfileMultilineRule("^Zusatz (.+)$", 1, 2, ProfileMultilineRule.Placement.AFTER), rule.type());
        ReceiptFormatDefinition profile = withItems(d, List.of(after));
        assertThat(new ReceiptFormatDefinitionValidator().validate(profile,
                normalizer.normalize(baseText().replace("SUMME", "Zusatz Regional\nSUMME"))).valid()).isTrue();
        ProfileParseOutcome outcome = interpret(profile, baseText().replace("SUMME", "Zusatz 0,00\nSUMME"));
        assertThat(outcome.parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
        assertThat(outcome.parseResult().receipt().items()).hasSize(1);
        assertThat(outcome.parseResult().receipt().items().getFirst().description()).isEqualTo("Bio Apfel");
        assertThat(outcome.traces().get(4).lineType()).isEqualTo(ParseLineType.UNRESOLVED);
    }

    private ProfileParseOutcome parse(String text) throws IOException { return interpret(definition(), text); }
    private ProfileParseOutcome interpret(ReceiptFormatDefinition definition, String text) {
        return gate.validate(interpreter.interpret(definition, normalizer.normalize(text)));
    }
    private ReceiptFormatDefinition definition() throws IOException { return codec.read(resource("format_profile_unresolved.profile.json")); }
    private ReceiptFormatDefinition withItems(ReceiptFormatDefinition d, List<ProfileItemRule> items) {
        return new ReceiptFormatDefinition(d.schemaVersion(), d.anchors(), d.fields(), items, d.lineRules());
    }
    private String baseText() { return "TESTMARKT\n13.07.2026\nARTIKEL PREIS\nBio Apfel 1,99\nSUMME 1,99\n"; }
    private String resource(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/corpus/profile/" + name)) {
            if (input == null) throw new IOException("Missing fixture: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
