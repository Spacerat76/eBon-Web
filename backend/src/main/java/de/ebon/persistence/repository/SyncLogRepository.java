package de.ebon.persistence.repository;

import de.ebon.persistence.model.SyncLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    List<SyncLog> findTop10ByOrderByStartedAtDesc();
}
