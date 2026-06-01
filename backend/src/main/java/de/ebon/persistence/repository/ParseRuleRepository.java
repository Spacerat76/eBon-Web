package de.ebon.persistence.repository;

import de.ebon.persistence.model.ParseRule;
import de.ebon.persistence.model.ParseRuleType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParseRuleRepository extends JpaRepository<ParseRule, Long> {

    List<ParseRule> findByActiveTrueAndRuleTypeOrderByStoreNameAsc(ParseRuleType ruleType);
}
