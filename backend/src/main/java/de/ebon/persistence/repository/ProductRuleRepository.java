package de.ebon.persistence.repository;

import de.ebon.persistence.model.ProductRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRuleRepository extends JpaRepository<ProductRule, Long> {

    List<ProductRule> findByActiveTrueOrderByPriorityAscIdAsc();
}
