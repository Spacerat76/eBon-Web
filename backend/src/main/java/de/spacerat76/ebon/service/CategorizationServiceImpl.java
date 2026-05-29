package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.CategorizationRule;
import de.spacerat76.ebon.repository.CategoryRepository;
import de.spacerat76.ebon.repository.CategorizationRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CategorizationServiceImpl implements CategorizationService {

    private final CategoryRepository categoryRepository;
    @Autowired(required = false)
    private CategorizationRuleRepository categorizationRuleRepository;

    public CategorizationServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // constructor used by tests and by Spring when both repositories are available
    @Autowired
    public CategorizationServiceImpl(CategoryRepository categoryRepository, CategorizationRuleRepository categorizationRuleRepository) {
        this.categoryRepository = categoryRepository;
        this.categorizationRuleRepository = categorizationRuleRepository;
    }

    @Override
    public void categorize(Receipt receipt) {
        if (receipt == null || receipt.getItems() == null) return;

        // load categorization rules if available, ordered by priority desc
        java.util.List<CategorizationRule> rules = java.util.Collections.emptyList();
        if (categorizationRuleRepository != null) {
            try {
                rules = categorizationRuleRepository.findAll();
                rules.sort((a, b) -> Integer.compare(b.getPriority() == null ? 0 : b.getPriority(), a.getPriority() == null ? 0 : a.getPriority()));
            } catch (Exception ignored) {}
        }

        for (var item : receipt.getItems()) {
            boolean assigned = false;
            String desc = item.getDescription() == null ? "" : item.getDescription();
            String store = receipt.getStoreName() == null ? "" : receipt.getStoreName();

            // rule-based matching
            for (CategorizationRule r : rules) {
                if (r == null || r.getPattern() == null) continue;
                try {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(r.getPattern(), java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE);
                    java.util.regex.Matcher md = p.matcher(desc);
                    java.util.regex.Matcher ms = p.matcher(store);
                    if (md.find() || ms.find()) {
                        if (r.getCategoryId() != null) {
                            try {
                                java.util.Optional<Category> oc = categoryRepository.findById(r.getCategoryId());
                                if (oc.isPresent()) {
                                    item.setCategory(oc.get());
                                    item.setCategorySource("RULE");
                                    assigned = true;
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!assigned) {
                // fallback heuristic
                String raw = (receipt.getRawText() == null ? "" : receipt.getRawText()).toLowerCase();
                String categoryName = raw.contains("supermarket") ? "Groceries" : "Uncategorized";
                Optional<Category> opt = categoryRepository.findByName(categoryName);
                Category cat = opt.orElseGet(() -> {
                    Category c = new Category();
                    c.setName(categoryName);
                    c.setIsActive(true);
                    return categoryRepository.save(c);
                });
                item.setCategory(cat);
                item.setCategorySource("AUTOMATIC");
            }
        }
    }
}
