package de.ebon.categorization;

import de.ebon.persistence.model.CategorizationRule;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.RuleMatchField;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

@Component
class CategorizationRuleMatcher {

    boolean matches(CategorizationRule rule, ReceiptItem item) {
        String target = target(rule, item);
        if (target == null || rule.getMatchValue() == null) {
            return false;
        }

        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        String normalizedValue = rule.getMatchValue().toLowerCase(Locale.ROOT);

        return switch (rule.getMatchType()) {
            case CONTAINS -> normalizedTarget.contains(normalizedValue);
            case STARTS_WITH -> normalizedTarget.startsWith(normalizedValue);
            case ENDS_WITH -> normalizedTarget.endsWith(normalizedValue);
            case EXACT -> normalizedTarget.equals(normalizedValue);
            case REGEX -> regexMatches(rule.getMatchValue(), target);
        };
    }

    private String target(CategorizationRule rule, ReceiptItem item) {
        if (rule.getMatchField() == RuleMatchField.DESCRIPTION) {
            return item.getDescription();
        }
        return item.getReceipt() == null ? null : item.getReceipt().getStoreName();
    }

    private boolean regexMatches(String regex, String target) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(target)
                    .find();
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }
}
