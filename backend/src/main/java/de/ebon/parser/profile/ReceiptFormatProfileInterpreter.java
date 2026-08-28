package de.ebon.parser.profile;

import de.ebon.parser.ParsedReceipt;
import de.ebon.parser.ParsedReceiptItem;
import de.ebon.persistence.model.ParseLineType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Executes previously schema-validated definitions. Every source line retains exactly one owner. */
@Component
public final class ReceiptFormatProfileInterpreter {
    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final ProfileRegex NUMBER = ProfileRegex.compile(
            "^-?(?:[0-9]+(?:[.,][0-9]+)?|[0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]+)?)$");
    private static final ProfileRegex PRICE_LIKE = ProfileRegex.compile(
            "(?i)(?:^|[^0-9])-?[0-9]+(?:\\.[0-9]{3})*(?:[.,][0-9]{2}(?:[^0-9]|$)|\\s*(?:EUR|€))");
    // Capture the entire numeric value, excluding a currency suffix; do not consume delimiters.
    private static final ProfileRegex MONETARY_TOKEN = ProfileRegex.compile(
            "(?i)(-?[0-9]+(?:\\.[0-9]{3})*[.,][0-9]{2,})|(-?[0-9]+(?:\\.[0-9]{3})*)\\s*(?:EUR|€)");
    private static final Map<ParseLineType, ProfileRegex> PROTECTED = Map.of(
            ParseLineType.TOTAL, ProfileRegex.compile("(?i)^\\s*(?:summe|sumne|gesamt(?:betrag|summe)?|zu\\s+zahlen|total|endbetrag)(?:\\b|\\s|:)"),
            ParseLineType.TAX, ProfileRegex.compile("(?i)^\\s*(?:mwst|ust|mehrwertsteuer|steuer|netto|brutto)(?:\\b|\\s|:)"),
            ParseLineType.METADATA, ProfileRegex.compile("(?i)^\\s*(?:tse|fiskal|signatur|transaktion)(?:\\b|\\s|:)"),
            ParseLineType.PAYMENT, ProfileRegex.compile("(?i)^\\s*(?:kartenzahlung|girocard|ec[- ]cash|ec[- ]karte|karte|bar(?:zahlung)?|visa|mastercard|zahlung|bezahlt|gegeben|rückgeld|rueckgeld|wechselgeld)(?:\\b|\\s|:)"));

    public ProfileInterpretationResult interpret(ReceiptFormatDefinition definition, NormalizedReceiptDocument document) {
        return new Evaluation(definition, document).run();
    }

    private static final class Evaluation {
        private final ReceiptFormatDefinition definition;
        private final NormalizedReceiptDocument document;
        private final ProfileRegex.Budget budget = new ProfileRegex.Budget();
        private final Map<String, ProfileRegex> compiled = new HashMap<>();
        private final Map<String, Integer> anchors = new HashMap<>();
        private final Map<ProfileFieldRule.Field, String> fields = new EnumMap<>(ProfileFieldRule.Field.class);
        private final List<Line> lines = new ArrayList<>();
        private final List<Candidate> candidates = new ArrayList<>();
        private final List<ParsedReceiptItem> items = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        Evaluation(ReceiptFormatDefinition definition, NormalizedReceiptDocument document) {
            this.definition = definition;
            this.document = document;
            for (NormalizedReceiptLine source : document.lines()) {
                lines.add(new Line(source));
            }
        }

        ProfileInterpretationResult run() {
            try {
                if (bounded()) {
                    extractAnchors();
                    extractFields();
                    extractCandidates();
                    classifyLines();
                    resolveCandidates();
                }
            } catch (ProfileDefinitionException exception) {
                error(exception.result().errors().getFirst().code().name());
            }
            ParsedReceipt receipt = receipt();
            List<ParsedLineTrace> traces = lines.stream().map(Line::trace).toList();
            return new ProfileInterpretationResult(receipt, traces, errors);
        }

        private boolean bounded() {
            long length = 0;
            int previousNumber = 0;
            int previousEnd = 0;
            if (document.lines().isEmpty() || document.lines().size() > ReceiptFormatDefinitionValidator.MAX_DOCUMENT_LINES) {
                error("INPUT_LIMIT");
                return false;
            }
            for (NormalizedReceiptLine source : document.lines()) {
                if (source.originalText() == null || source.originalText().length() > ProfileRegex.MAX_LINE_LENGTH
                        || source.originalLineNumber() <= previousNumber || source.startOffset() < previousEnd
                        || source.endOffset() - source.startOffset() != source.originalText().length()) {
                    error("INPUT_LIMIT");
                    return false;
                }
                length += source.originalText().length();
                previousNumber = source.originalLineNumber();
                previousEnd = source.endOffset();
            }
            long ruleCount = (long) definition.anchors().size() + definition.fields().size()
                    + definition.itemRules().size() + definition.lineRules().size();
            if (length > ReceiptFormatDefinitionValidator.MAX_DOCUMENT_LENGTH) error("INPUT_LIMIT");
            if (ruleCount > ReceiptFormatDefinitionValidator.MAX_RULES) error("PROFILE_LIMIT");
            if (definition.schemaVersion() != 1) error("SCHEMA_VERSION");
            return errors.isEmpty();
        }

        private void extractAnchors() {
            for (ProfileAnchor anchor : definition.anchors()) {
                List<Integer> hits = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    if (!matches(anchor.regex(), i).isEmpty()) {
                        hits.add(i);
                        lines.get(i).anchor = true;
                    }
                }
                if (anchor.required() && hits.size() != 1) {
                    error(hits.isEmpty() ? "ANCHOR_NOT_FOUND" : "AMBIGUOUS_ANCHOR");
                }
                if (hits.size() == 1) anchors.put(anchor.id(), hits.getFirst());
            }
        }

        private void extractFields() {
            for (ProfileFieldRule rule : definition.fields()) {
                List<String> values = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    for (ProfileRegex.Match match : matches(rule.regex(), i)) {
                        String value = match.group(rule.captureGroup());
                        if (value == null || value.isBlank()) {
                            error("EMPTY_FIELD_CAPTURE");
                            lines.get(i).unresolved("EMPTY_FIELD_CAPTURE");
                        } else {
                            value = value.trim();
                            values.add(value);
                            lines.get(i).extracted.put(rule.field().name(), value);
                            lines.get(i).fieldMatched = true;
                            lines.get(i).totalField |= rule.field() == ProfileFieldRule.Field.TOTAL_AMOUNT;
                            lines.get(i).coverage.add(match.groupSpan(rule.captureGroup()));
                        }
                    }
                }
                if (values.size() == 1) {
                    fields.put(rule.field(), values.getFirst());
                } else if (values.size() > 1) {
                    error("AMBIGUOUS_FIELD");
                } else if (rule.required()) {
                    error("REQUIRED_FIELD_MISSING");
                }
            }
            for (Line line : lines) {
                if (line.fieldMatched) {
                    line.types.add(line.totalField ? ParseLineType.TOTAL : ParseLineType.METADATA);
                }
            }
        }

        private void extractCandidates() {
            for (ProfileItemRule rule : definition.itemRules()) {
                Integer from = anchors.get(rule.region().startAnchor());
                Integer to = anchors.get(rule.region().endAnchor());
                if (from == null || to == null || from >= to) {
                    error("INVALID_ITEM_REGION");
                    continue;
                }
                for (int i = from + 1; i < to; i++) {
                    for (ProfileRegex.Match match : matches(rule.regex(), i)) {
                        Map<ProfileItemRule.Field, String> captures = new EnumMap<>(ProfileItemRule.Field.class);
                        rule.captures().forEach((field, group) -> captures.put(field, match.group(group)));
                        Candidate candidate = new Candidate(i, captures);
                        rule.captures().values().forEach(group -> {
                            if (match.groupSpan(group) != null) candidate.coverage.add(match.groupSpan(group));
                        });
                        candidate.descriptions.put(i, captures.get(ProfileItemRule.Field.DESCRIPTION));
                        extend(candidate, rule.multiline(), from, to);
                        candidates.add(candidate);
                    }
                }
            }
        }

        private void extend(Candidate candidate, ProfileMultilineRule multiline, int from, int to) {
            if (multiline == null) return;
            int step = multiline.placement() == ProfileMultilineRule.Placement.BEFORE ? -1 : 1;
            int previous = candidate.priceLine;
            for (int count = 1; count < multiline.maxLines(); count++) {
                int index = previous + step;
                if (index <= from || index >= to
                        || lines.get(index).source.originalLineNumber() != lines.get(previous).source.originalLineNumber() + step) {
                    break;
                }
                List<ProfileRegex.Match> matches = matches(multiline.regex(), index);
                if (matches.isEmpty()) break;
                // A description-only capture cannot account for another monetary position.
                if (!PRICE_LIKE.findAll(lines.get(index).source.originalText(), budget).isEmpty()) break;
                String description = matches.getFirst().group(multiline.descriptionGroup());
                candidate.descriptions.put(index, description);
                if (matches.size() != 1 || description == null || description.isBlank()) candidate.invalid = true;
                previous = index;
            }
        }

        private void classifyLines() {
            for (ProfileLineRule rule : definition.lineRules()) {
                for (int i = 0; i < lines.size(); i++) {
                    List<ProfileRegex.Match> matches = matches(rule.regex(), i);
                    if (!matches.isEmpty()) {
                        Line line = lines.get(i);
                        line.types.add(rule.type() == ProfileLineRule.Type.TSE ? ParseLineType.METADATA
                                : ParseLineType.valueOf(rule.type().name()));
                        matches.forEach(match -> line.coverage.add(new ProfileRegex.Span(match.start(), match.end())));
                    }
                }
            }
            for (Line line : lines) {
                for (ProfileRegex protectedRule : PROTECTED.values()) {
                    if (!protectedRule.findAll(line.source.originalText(), budget).isEmpty() && line.types.isEmpty()) {
                        // Keywords reject item candidates; only explicit field/line rules resolve coverage.
                        line.unresolved("LEXICAL_GUARD_ONLY");
                    }
                }
                if (line.types.size() > 1) line.unresolved("CLASSIFICATION_COLLISION");
                if ((!line.types.isEmpty() || line.anchor) && hasUncoveredMoney(line, line.coverage)) {
                    line.unresolved("UNCONSUMED_MONETARY_CONTENT");
                }
            }
        }

        private void resolveCandidates() {
            Map<Integer, Integer> owners = new HashMap<>();
            candidates.forEach(candidate -> candidate.descriptions.keySet().forEach(index -> owners.merge(index, 1, Integer::sum)));
            candidates.sort(Comparator.comparingInt(candidate -> candidate.descriptions.firstKey()));
            for (Candidate candidate : candidates) {
                if (hasUncoveredMoney(lines.get(candidate.priceLine), candidate.coverage)) {
                    candidate.descriptions.keySet().forEach(index -> lines.get(index).unresolved("UNCONSUMED_MONETARY_CONTENT"));
                    continue;
                }
                boolean collision = candidate.descriptions.keySet().stream().anyMatch(index -> {
                    Line line = lines.get(index);
                    return owners.get(index) > 1 || line.anchor || !line.types.isEmpty() || line.unresolved;
                });
                if (collision || candidate.invalid) {
                    candidate.descriptions.keySet().forEach(index -> lines.get(index).unresolved("ITEM_COLLISION"));
                    continue;
                }
                int errorsBefore = errors.size();
                String description = description(candidate);
                ParsedReceiptItem item = new ParsedReceiptItem(items.size(), description,
                        decimal(candidate.captures.get(ProfileItemRule.Field.QUANTITY)),
                        candidate.captures.get(ProfileItemRule.Field.UNIT),
                        decimal(candidate.captures.get(ProfileItemRule.Field.UNIT_PRICE)),
                        decimal(candidate.captures.get(ProfileItemRule.Field.TOTAL_PRICE)),
                        decimal(candidate.captures.get(ProfileItemRule.Field.DISCOUNT_AMOUNT)));
                if (description == null || item.totalPrice() == null || errors.size() != errorsBefore
                        || candidate.captures.values().stream().anyMatch(value -> value == null || value.isBlank())) {
                    error("INVALID_ITEM");
                    candidate.descriptions.keySet().forEach(index -> lines.get(index).unresolved("INVALID_ITEM"));
                    continue;
                }
                items.add(item);
                candidate.descriptions.forEach((index, part) -> {
                    Line line = lines.get(index);
                    line.position = item.positionIndex();
                    line.types.add(ParseLineType.POSITION);
                    line.extracted.put("DESCRIPTION", part.trim());
                });
                candidate.captures.forEach((field, value) -> lines.get(candidate.priceLine).extracted.put(field.name(), value.trim()));
            }
        }

        private String description(Candidate candidate) {
            if (candidate.descriptions.values().stream().anyMatch(value -> value == null || value.isBlank())) return null;
            return String.join(" ", candidate.descriptions.values().stream().map(String::trim).toList());
        }

        private boolean hasUncoveredMoney(Line line, List<ProfileRegex.Span> coverage) {
            if (line.monetaryTokens == null) {
                line.monetaryTokens = MONETARY_TOKEN.findAll(line.source.originalText(), budget).stream()
                        .map(match -> match.groupSpan(1) != null ? match.groupSpan(1) : match.groupSpan(2)).toList();
            }
            return line.monetaryTokens.stream().anyMatch(token -> coverage.stream().noneMatch(span -> span.contains(token)));
        }

        private ParsedReceipt receipt() {
            return new ParsedReceipt(date(fields.get(ProfileFieldRule.Field.RECEIPT_DATE)),
                    time(fields.get(ProfileFieldRule.Field.RECEIPT_TIME)), fields.get(ProfileFieldRule.Field.STORE_NAME),
                    fields.get(ProfileFieldRule.Field.STORE_BRANCH), decimal(fields.get(ProfileFieldRule.Field.TOTAL_AMOUNT)),
                    fields.get(ProfileFieldRule.Field.CURRENCY), decimal(fields.get(ProfileFieldRule.Field.BONUS_BALANCE)),
                    decimal(fields.get(ProfileFieldRule.Field.BONUS_POINTS)), fields.get(ProfileFieldRule.Field.BONUS_TYPE), items);
        }

        private LocalDate date(String value) {
            if (value == null) return null;
            try {
                return value.indexOf('.') >= 0 ? LocalDate.parse(value, GERMAN_DATE) : LocalDate.parse(value);
            } catch (DateTimeParseException exception) {
                error("INVALID_DATE");
                return null;
            }
        }

        private LocalTime time(String value) {
            if (value == null) return null;
            try {
                return LocalTime.parse(value);
            } catch (DateTimeParseException exception) {
                error("INVALID_TIME");
                return null;
            }
        }

        private BigDecimal decimal(String value) {
            if (value == null) return null;
            try {
                String normalized = value.trim();
                if (NUMBER.findAll(normalized, budget).isEmpty()) {
                    error("INVALID_NUMBER");
                    return null;
                }
                if (normalized.indexOf(',') >= 0) normalized = normalized.replace(".", "").replace(',', '.');
                else if (normalized.indexOf('.') != normalized.lastIndexOf('.')) normalized = normalized.replace(".", "");
                return new BigDecimal(normalized);
            } catch (ProfileDefinitionException exception) {
                error(exception.result().errors().getFirst().code().name());
                return null;
            } catch (NumberFormatException exception) {
                error("INVALID_NUMBER");
                return null;
            }
        }

        private List<ProfileRegex.Match> matches(String regex, int index) {
            return compiled.computeIfAbsent(regex, ProfileRegex::compile).findAll(lines.get(index).source.originalText(), budget);
        }

        private void error(String code) {
            // Keep per-occurrence entries: item-local conversion failures must not disappear after a prior failure.
            errors.add(code);
        }
    }

    private static final class Candidate {
        final int priceLine;
        final Map<ProfileItemRule.Field, String> captures;
        final TreeMap<Integer, String> descriptions = new TreeMap<>();
        final List<ProfileRegex.Span> coverage = new ArrayList<>();
        boolean invalid;

        Candidate(int priceLine, Map<ProfileItemRule.Field, String> captures) {
            this.priceLine = priceLine;
            this.captures = captures;
        }
    }

    private static final class Line {
        final NormalizedReceiptLine source;
        final Set<ParseLineType> types = EnumSet.noneOf(ParseLineType.class);
        final Map<String, String> extracted = new LinkedHashMap<>();
        final List<ProfileRegex.Span> coverage = new ArrayList<>();
        List<ProfileRegex.Span> monetaryTokens;
        boolean anchor;
        boolean fieldMatched;
        boolean totalField;
        boolean unresolved;
        String reason;
        Integer position;

        Line(NormalizedReceiptLine source) { this.source = source; }

        void unresolved(String reason) {
            this.unresolved = true;
            this.reason = reason;
            this.position = null;
        }

        ParsedLineTrace trace() {
            ParseLineType type = unresolved ? ParseLineType.UNRESOLVED
                    : types.size() == 1 ? types.iterator().next() : anchor ? ParseLineType.METADATA : ParseLineType.UNRESOLVED;
            String explanation = reason != null ? reason : type == ParseLineType.UNRESOLVED ? "NO_MATCHING_RULE"
                    : type == ParseLineType.POSITION ? "ITEM_RULE" : anchor && types.isEmpty() ? "ANCHOR" : "CLASSIFIED";
            return new ParsedLineTrace(source.originalLineNumber(), source.startOffset(), source.endOffset(), type,
                    position, extracted, explanation);
        }
    }
}
