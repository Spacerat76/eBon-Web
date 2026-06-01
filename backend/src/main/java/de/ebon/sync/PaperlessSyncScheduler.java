package de.ebon.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.sync.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
class PaperlessSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaperlessSyncScheduler.class);

    private final PaperlessSyncService syncService;

    PaperlessSyncScheduler(PaperlessSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(
            fixedDelayString = "#{${app.sync.scheduler.interval-minutes:60} * 60000}",
            initialDelayString = "${app.sync.scheduler.initial-delay-ms:3600000}")
    void runScheduledSync() {
        try {
            syncService.synchronize();
        } catch (SyncAlreadyRunningException exception) {
            LOGGER.info("Scheduled Paperless sync skipped because another sync is already running.");
        }
    }
}
