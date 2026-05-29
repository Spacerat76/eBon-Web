package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.CategorizationRule;
import de.spacerat76.ebon.domain.ReceiptItem;
import de.spacerat76.ebon.repository.CategorizationRuleRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class RuleAdaptationService {

    private final CategorizationRuleRepository categorizationRuleRepository;

    public RuleAdaptationService(CategorizationRuleRepository categorizationRuleRepository) {
        this.categorizationRuleRepository = categorizationRuleRepository;
    }

    /**
     * Create a simple categorization rule from a manually categorized item.
     * This is a naive implementation that stores the item description as the pattern.
     */
    public void adaptRuleForManualCategorization(ReceiptItem item) {
        if (item == null || item.getCategory() == null) return;

        CategorizationRule r = new CategorizationRule();
        r.setName("auto:" + item.getCategory().getName());
        r.setDescription("Auto-created from manual categorization");
        r.setPattern(item.getDescription());
        r.setCategoryId(item.getCategory().getId());
        r.setPriority(0);
        r.setCreatedAt(OffsetDateTime.now());

        categorizationRuleRepository.save(r);
    }
}
