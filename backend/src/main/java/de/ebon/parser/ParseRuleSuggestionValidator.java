package de.ebon.parser;

import de.ebon.persistence.model.ParseRuleType;
import de.ebon.persistence.model.ParseRuleValidationStatus;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

@Component
public class ParseRuleSuggestionValidator {

    public ValidationResult validate(String rawText, ParseRuleType ruleType, String regex, String extractGroup) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        } catch (PatternSyntaxException exception) {
            return new ValidationResult(ParseRuleValidationStatus.INVALID_REGEX, exception.getDescription());
        }

        Matcher matcher = pattern.matcher(rawText == null ? "" : rawText);
        if (!matcher.find()) {
            return new ValidationResult(ParseRuleValidationStatus.NO_MATCH, "Regex passt nicht auf den Beispiel-Bon.");
        }

        String matched = matcher.group();
        if (ruleType == ParseRuleType.ITEM_PATTERN && isCollisionRisk(matched)) {
            return new ValidationResult(
                    ParseRuleValidationStatus.COLLISION_RISK,
                    "Regex trifft eine offensichtliche Steuer-, TSE-, Summen- oder Zahlungszeile.");
        }

        if (extractGroup != null && !extractGroup.isBlank() && !extractGroupsAvailable(matcher, extractGroup)) {
            return new ValidationResult(
                    ParseRuleValidationStatus.WRONG_EXTRACTION,
                    "Mindestens eine angegebene Extract-Group ist im Treffer nicht verfuegbar.");
        }

        return new ValidationResult(ParseRuleValidationStatus.VALID, null);
    }

    private boolean extractGroupsAvailable(Matcher matcher, String extractGroup) {
        for (String group : extractGroup.split(",")) {
            String trimmed = group.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            try {
                String value = trimmed.matches("\\d+") ? matcher.group(Integer.parseInt(trimmed)) : matcher.group(trimmed);
                if (value == null || value.isBlank()) {
                    return false;
                }
            } catch (RuntimeException exception) {
                return false;
            }
        }
        return true;
    }

    private boolean isCollisionRisk(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        return upper.contains("TSE")
                || upper.contains("FISKAL")
                || upper.contains("MWST")
                || upper.contains("UST")
                || upper.contains("STEUER")
                || upper.contains("SUMME")
                || upper.contains("GESAMT")
                || upper.contains("ZU ZAHLEN")
                || upper.contains("KARTENZAHLUNG")
                || upper.contains("GIROCARD")
                || upper.contains("EC-CASH");
    }

    public record ValidationResult(ParseRuleValidationStatus status, String message) {
    }
}
