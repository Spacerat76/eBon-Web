package de.ebon.persistence.repository;

import de.ebon.persistence.model.ParseRuleSuggestion;
import de.ebon.persistence.model.ParseRuleSuggestionStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ParseRuleSuggestionRepository
        extends JpaRepository<ParseRuleSuggestion, Long>, JpaSpecificationExecutor<ParseRuleSuggestion> {

    long countByReceipt_IdAndStatus(Long receiptId, ParseRuleSuggestionStatus status);

    List<ParseRuleSuggestion> findByReceipt_IdOrderByCreatedAtDesc(Long receiptId);

    Page<ParseRuleSuggestion> findByStatusOrderByCreatedAtDesc(ParseRuleSuggestionStatus status, Pageable pageable);

    List<ParseRuleSuggestion> findByStatusAndAcceptedParseRuleIsNotNullOrderByIdAsc(ParseRuleSuggestionStatus status);
}
