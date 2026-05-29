package de.spacerat76.ebon.repository;

import de.spacerat76.ebon.domain.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {
}
