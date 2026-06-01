package de.ebon.persistence.repository;

import de.ebon.persistence.model.SyncLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    List<SyncLog> findTop10ByOrderByStartedAtDesc();

    Page<SyncLog> findAllByOrderByStartedAtDesc(Pageable pageable);

    Optional<SyncLog> findFirstByOrderByStartedAtDesc();
}
