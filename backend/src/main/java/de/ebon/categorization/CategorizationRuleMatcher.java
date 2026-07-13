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
        if (!matchesStoreConstraint(rule, item)) {
            return false;
        }
        String target = target(rule, item);
        if (target == null || rule.getMatchValue() == null) {
            return false;
        }

        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        String normalizedValue = rule.getMatchValue().toLowerCase(Locale.ROOT);
        String compactTarget = compact(target);
        String compactValue = compact(rule.getMatchValue());

        return switch (rule.getMatchType()) {
            case CONTAINS -> normalizedTarget.contains(normalizedValue)
                    || (!compactValue.isEmpty() && compactTarget.contains(compactValue));
            case STARTS_WITH -> normalizedTarget.startsWith(normalizedValue)
                    || (!compactValue.isEmpty() && compactTarget.startsWith(compactValue));
            case ENDS_WITH -> normalizedTarget.endsWith(normalizedValue)
                    || (!compactValue.isEmpty() && compactTarget.endsWith(compactValue));
            case EXACT -> normalizedTarget.equals(normalizedValue)
                    || (!compactValue.isEmpty() && compactTarget.equals(compactValue));
            case REGEX -> regexMatches(rule.getMatchValue(), target);
        };
    }

    private boolean matchesStoreConstraint(CategorizationRule rule, ReceiptItem item) {
        if (rule.getStoreName() == null) {
            return true;
        }
        String actual = item.getReceipt() == null ? null : item.getReceipt().getStoreName();
        return actual != null && actual.trim().equalsIgnoreCase(rule.getStoreName());
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

    private String compact(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]", "");
    }
}
