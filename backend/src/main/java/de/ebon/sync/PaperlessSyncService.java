package de.ebon.sync;

import de.ebon.persistence.repository.SyncLogRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class PaperlessSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaperlessSyncService.class);

    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final PaperlessSyncRunner syncRunner;
    private final SyncLogRepository syncLogRepository;
    private final TaskExecutor syncTaskExecutor;

    public PaperlessSyncService(
            PaperlessSyncRunner syncRunner,
            SyncLogRepository syncLogRepository,
            @Qualifier("syncTaskExecutor") TaskExecutor syncTaskExecutor) {
        this.syncRunner = syncRunner;
        this.syncLogRepository = syncLogRepository;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    public void triggerAsync() {
        acquireLock();
        syncTaskExecutor.execute(() -> {
            try {
                syncRunner.run();
            } catch (RuntimeException exception) {
                LOGGER.error("Unexpected Paperless sync failure.", exception);
            } finally {
                syncing.set(false);
            }
        });
    }

    public SyncRunResult synchronize() {
        acquireLock();
        try {
            return syncRunner.run();
        } finally {
            syncing.set(false);
        }
    }

    public SyncStatusDto currentStatus() {
        return SyncStatusDto.from(syncLogRepository.findFirstByOrderByStartedAtDesc(), syncing.get());
    }

    private void acquireLock() {
        if (!syncing.compareAndSet(false, true)) {
            throw new SyncAlreadyRunningException();
        }
    }
}
