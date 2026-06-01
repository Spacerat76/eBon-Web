package de.ebon.persistence.repository;

import de.ebon.persistence.model.SyncLogEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogEntryRepository extends JpaRepository<SyncLogEntry, Long> {

    List<SyncLogEntry> findBySyncLog_IdOrderByCreatedAtAsc(Long syncLogId);
}
