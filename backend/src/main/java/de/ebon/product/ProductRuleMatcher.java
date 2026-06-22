package de.ebon.product;

import de.ebon.persistence.model.ProductRule;
import de.ebon.persistence.model.ReceiptItem;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

@Component
class ProductRuleMatcher {

    boolean matches(ProductRule rule, ReceiptItem item) {
        if (!storeMatches(rule, item) || item.getDescription() == null || rule.getMatchValue() == null) {
            return false;
        }

        String target = item.getDescription();
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

    boolean isStoreSpecific(ProductRule rule) {
        return rule.getStoreName() != null && !rule.getStoreName().isBlank();
    }

    private boolean storeMatches(ProductRule rule, ReceiptItem item) {
        if (!isStoreSpecific(rule)) {
            return true;
        }
        String storeName = item.getReceipt() == null ? null : item.getReceipt().getStoreName();
        return storeName != null && compact(storeName).equals(compact(rule.getStoreName()));
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

    String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]", "");
    }
}
