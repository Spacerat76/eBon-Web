package de.spacerat76.ebon.repository;

import de.spacerat76.ebon.domain.SyncLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncLogEntryRepository extends JpaRepository<SyncLogEntry, Long> {
	List<SyncLogEntry> findBySyncLogIdOrderByIdDesc(Long syncLogId);
}
