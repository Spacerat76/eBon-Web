package de.ebon.persistence.repository;

import de.ebon.persistence.model.AiCategorizationLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCategorizationLogRepository extends JpaRepository<AiCategorizationLog, Long> {

    @Query("""
            select log
            from AiCategorizationLog log
            left join fetch log.suggestedCategory
            where log.receiptItem.id = :receiptItemId
                and log.assignedCategory is null
                and (
                    log.suggestedCategory is not null
                    or log.suggestedCategoryName is not null
                    or log.rejectionReason is not null
                )
            order by log.createdAt desc, log.id desc
            """)
    List<AiCategorizationLog> findLatestRejectedSuggestion(
            @Param("receiptItemId") Long receiptItemId,
            Pageable pageable);
}
