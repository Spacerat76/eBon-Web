package de.ebon.persistence.repository;

import de.ebon.persistence.model.CategorizationRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {

    List<CategorizationRule> findByActiveTrueOrderByPriorityAscIdAsc();

    List<CategorizationRule> findAllByOrderByPriorityAscIdAsc();

    boolean existsByCategory_Id(Long categoryId);
}
