package de.ebon.parser.profile;

import static de.ebon.parser.profile.ProfileValidationError.Code.*;

import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ReceiptFormatDefinitionValidator {
    public static final int MAX_RULES = 128;
    public static final int MAX_DOCUMENT_LINES = 4096;
    public static final int MAX_DOCUMENT_LENGTH = 262_144;
    private static final Pattern ANCHOR_ID_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Pattern PROTECTED_LINE = Pattern.compile(
            "(?i)^\\s*(?:summe|sumne|gesamt(?:betrag|summe)?|zu\\s+zahlen|total|endbetrag|"
                    + "mwst|ust|mehrwertsteuer|steuer|netto|brutto|tse|fiskal|signatur|transaktion|"
                    + "kartenzahlung|girocard|ec[- ]cash|ec[- ]karte|karte|bar(?:zahlung)?|visa|mastercard|"
                    + "zahlung|bezahlt|gegeben|rückgeld|rueckgeld|wechselgeld)(?:\\b|\\s|:)");
    private static final Set<ProfileFieldRule.Field> REQUIRED_FIELDS = EnumSet.of(
            ProfileFieldRule.Field.STORE_NAME, ProfileFieldRule.Field.RECEIPT_DATE,
            ProfileFieldRule.Field.TOTAL_AMOUNT);

    public ProfileValidationResult validate(ReceiptFormatDefinition definition, NormalizedReceiptDocument example) {
        Checks checks = new Checks();
        if (definition == null || example == null) {
            checks.error("$", MISSING_VALUE);
            return checks.result();
        }
        if (definition.schemaVersion() != 1) {
            checks.error("schemaVersion", SCHEMA_VERSION);
        }
        long rules = (long) definition.anchors().size() + definition.fields().size()
                + definition.itemRules().size() + definition.lineRules().size();
        if (rules > MAX_RULES) {
            checks.error("$", PROFILE_LIMIT);
            return checks.result();
        }
        if (!boundedDocument(example)) {
            checks.error("$", INPUT_LIMIT);
            return checks.result();
        }
        try {
            Map<String, AnchorMatch> anchors = validateAnchors(definition, example, checks);
            validateFields(definition, example, checks);
            Set<Integer> protectedLines = protectedLines(definition, example, checks);
            if (definition.itemRules().isEmpty()) {
                checks.error("itemRules", NO_EXAMPLE_MATCH);
            }
            for (int i = 0; i < definition.itemRules().size(); i++) {
                validateItem(definition.itemRules().get(i), "itemRules[" + i + "]", anchors,
                        example, protectedLines, checks);
            }
        } catch (ProfileDefinitionException exception) {
            // Budget/zero failure is fatal for the entire example, never a partial success.
            checks.error("$", exception.result().errors().getFirst().code());
        }
        return checks.result();
    }

    private boolean boundedDocument(NormalizedReceiptDocument document) {
        if (document.lines().isEmpty() || document.lines().size() > MAX_DOCUMENT_LINES) {
            return false;
        }
        long length = 0;
        for (NormalizedReceiptLine line : document.lines()) {
            if (line.originalText() == null || line.originalText().length() > ProfileRegex.MAX_LINE_LENGTH) {
                return false;
            }
            length += line.originalText().length();
        }
        return length <= MAX_DOCUMENT_LENGTH;
    }

    private Map<String, AnchorMatch> validateAnchors(ReceiptFormatDefinition definition,
            NormalizedReceiptDocument document, Checks checks) {
        Map<String, AnchorMatch> anchors = new HashMap<>();
        if (definition.anchors().stream().noneMatch(ProfileAnchor::required)) {
            checks.error("anchors", REQUIRED_ANCHOR);
        }
        for (int i = 0; i < definition.anchors().size(); i++) {
            ProfileAnchor anchor = definition.anchors().get(i);
            String path = "anchors[" + i + "]";
            if (anchor.id() == null || !ANCHOR_ID_PATTERN.matches(anchor.id())) {
                checks.error(path + ".id", ANCHOR_ID);
            }
            ProfileRegex regex = checks.compile(anchor.regex(), path + ".regex");
            List<Integer> hits = new ArrayList<>();
            if (regex != null) {
                for (int line = 0; line < document.lines().size(); line++) {
                    if (!checks.matches(regex, document.lines().get(line)).isEmpty()) {
                        hits.add(line);
                    }
                }
                if (anchor.required() && hits.isEmpty()) {
                    checks.error(path, ANCHOR_NOT_FOUND);
                }
                if (anchor.required() && hits.size() > 1) {
                    checks.error(path, AMBIGUOUS_ANCHOR);
                }
            }
            if (anchors.putIfAbsent(anchor.id(), new AnchorMatch(anchor.required(), hits)) != null) {
                checks.error(path + ".id", DUPLICATE_ANCHOR);
            }
        }
        return anchors;
    }

    private void validateFields(ReceiptFormatDefinition definition, NormalizedReceiptDocument document, Checks checks) {
        Set<ProfileFieldRule.Field> seen = EnumSet.noneOf(ProfileFieldRule.Field.class);
        Set<ProfileFieldRule.Field> required = EnumSet.noneOf(ProfileFieldRule.Field.class);
        for (int i = 0; i < definition.fields().size(); i++) {
            ProfileFieldRule rule = definition.fields().get(i);
            String path = "fields[" + i + "]";
            if (rule.field() == null) {
                checks.error(path + ".field", MISSING_VALUE);
            } else {
                if (!seen.add(rule.field())) {
                    checks.error(path + ".field", DUPLICATE_FIELD);
                }
                if (rule.required()) {
                    required.add(rule.field());
                }
            }
            ProfileRegex regex = checks.compile(rule.regex(), path + ".regex");
            if (regex == null || !checks.capture(regex, rule.captureGroup(), path + ".captureGroup")) {
                continue;
            }
            boolean matched = false;
            for (NormalizedReceiptLine line : document.lines()) {
                for (ProfileRegex.Match match : checks.matches(regex, line)) {
                    matched = true;
                    checks.nonempty(match, rule.captureGroup(), path + ".captureGroup");
                }
            }
            if (rule.required() && !matched) {
                checks.error(path, NO_EXAMPLE_MATCH);
            }
        }
        if (!required.containsAll(REQUIRED_FIELDS)) {
            checks.error("fields", REQUIRED_FIELD);
        }
    }

    private Set<Integer> protectedLines(ReceiptFormatDefinition definition,
            NormalizedReceiptDocument document, Checks checks) {
        Set<Integer> result = new HashSet<>();
        for (int i = 0; i < document.lines().size(); i++) {
            if (PROTECTED_LINE.matcher(document.lines().get(i).originalText()).find()) {
                result.add(i);
            }
        }
        for (int i = 0; i < definition.lineRules().size(); i++) {
            ProfileLineRule rule = definition.lineRules().get(i);
            String path = "lineRules[" + i + "]";
            if (rule.type() == null) {
                checks.error(path + ".type", MISSING_VALUE);
            }
            ProfileRegex regex = checks.compile(rule.regex(), path + ".regex");
            if (regex != null) {
                for (int line = 0; line < document.lines().size(); line++) {
                    if (!checks.matches(regex, document.lines().get(line)).isEmpty()
                            && rule.type() != ProfileLineRule.Type.IGNORED_SAFE) {
                        result.add(line);
                    }
                }
            }
        }
        return result;
    }

    private void validateItem(ProfileItemRule rule, String path, Map<String, AnchorMatch> anchors,
            NormalizedReceiptDocument document, Set<Integer> protectedLines, Checks checks) {
        if (rule.type() == null) {
            checks.error(path + ".type", MISSING_VALUE);
        }
        ProfileRegex regex = checks.compile(rule.regex(), path + ".regex");
        if (!rule.captures().keySet().containsAll(Set.of(
                ProfileItemRule.Field.DESCRIPTION, ProfileItemRule.Field.TOTAL_PRICE))) {
            checks.error(path + ".captures", REQUIRED_CAPTURE);
        }
        if (regex != null) {
            rule.captures().forEach((field, group) -> checks.capture(regex, group, path + ".captures." + field.name()));
        }
        int[] region = region(rule.region(), path + ".region", anchors, checks);
        boolean exampleMatched = false;
        if (regex != null) {
            for (int i = 0; i < document.lines().size(); i++) {
                List<ProfileRegex.Match> matches = checks.matches(regex, document.lines().get(i));
                if (!matches.isEmpty() && protectedLines.contains(i)) {
                    checks.error(path, ITEM_COLLISION);
                }
                if (region != null && i > region[0] && i < region[1] && !matches.isEmpty()) {
                    exampleMatched = true;
                    for (ProfileRegex.Match match : matches) {
                        rule.captures().forEach((field, group) -> {
                            if (group > 0 && group <= regex.groupCount()) {
                                checks.nonempty(match, group, path + ".captures." + field.name());
                            }
                        });
                    }
                }
            }
            if (!exampleMatched) {
                checks.error(path, NO_EXAMPLE_MATCH);
            }
        }
        if (rule.multiline() != null) {
            validateMultiline(rule.multiline(), path + ".multiline", document, protectedLines, checks);
        }
    }

    private int[] region(ProfileItemRegion region, String path, Map<String, AnchorMatch> anchors, Checks checks) {
        AnchorMatch start = region == null ? null : anchors.get(region.startAnchor());
        AnchorMatch end = region == null ? null : anchors.get(region.endAnchor());
        if (start == null || end == null || !start.required() || !end.required()
                || start.lines().size() != 1 || end.lines().size() != 1) {
            checks.error(path, REGION_ANCHOR);
            return null;
        }
        int from = start.lines().getFirst();
        int to = end.lines().getFirst();
        if (from >= to) {
            checks.error(path, REGION_ORDER);
            return null;
        }
        return new int[] {from, to};
    }

    private void validateMultiline(ProfileMultilineRule rule, String path, NormalizedReceiptDocument document,
            Set<Integer> protectedLines, Checks checks) {
        if (rule.maxLines() < 2 || rule.maxLines() > 8) {
            checks.error(path + ".maxLines", MULTILINE_LIMIT);
        }
        if (rule.placement() == null) {
            checks.error(path + ".placement", MISSING_VALUE);
        }
        ProfileRegex regex = checks.compile(rule.regex(), path + ".regex");
        if (regex == null || !checks.capture(regex, rule.descriptionGroup(), path + ".descriptionGroup")) {
            return;
        }
        for (int i = 0; i < document.lines().size(); i++) {
            List<ProfileRegex.Match> matches = checks.matches(regex, document.lines().get(i));
            if (!matches.isEmpty() && protectedLines.contains(i)) {
                checks.error(path, ITEM_COLLISION);
            }
            for (ProfileRegex.Match match : matches) {
                checks.nonempty(match, rule.descriptionGroup(), path + ".descriptionGroup");
            }
        }
    }

    private record AnchorMatch(boolean required, List<Integer> lines) {
        private AnchorMatch {
            lines = List.copyOf(lines);
        }
    }

    private static final class Checks {
        private final List<ProfileValidationError> errors = new ArrayList<>();
        private final ProfileRegex.Budget budget = new ProfileRegex.Budget();

        void error(String path, ProfileValidationError.Code code) {
            ProfileValidationError error = new ProfileValidationError(path, code);
            if (!errors.contains(error)) {
                errors.add(error);
            }
        }

        ProfileRegex compile(String regex, String path) {
            try {
                return ProfileRegex.compile(regex);
            } catch (ProfileDefinitionException exception) {
                error(path, exception.result().errors().getFirst().code());
                return null;
            }
        }

        boolean capture(ProfileRegex regex, int group, String path) {
            if (group < 1 || group > regex.groupCount()) {
                error(path, CAPTURE_GROUP);
                return false;
            }
            return true;
        }

        void nonempty(ProfileRegex.Match match, int group, String path) {
            if (match.group(group) == null || match.group(group).isBlank()) {
                error(path, EMPTY_CAPTURE);
            }
        }

        List<ProfileRegex.Match> matches(ProfileRegex regex, NormalizedReceiptLine line) {
            return regex.findAll(line.originalText(), budget);
        }

        ProfileValidationResult result() {
            return new ProfileValidationResult(errors);
        }
    }
}
