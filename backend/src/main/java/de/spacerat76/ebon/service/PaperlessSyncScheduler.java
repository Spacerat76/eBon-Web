package de.spacerat76.ebon.service;

import de.spacerat76.ebon.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaperlessSyncScheduler {

    private final PaperlessSyncService syncService;
    private final AppProperties appProperties;

    public PaperlessSyncScheduler(PaperlessSyncService syncService, AppProperties appProperties) {
        this.syncService = syncService;
        this.appProperties = appProperties;
    }

    // Run with a delay of `ebon.sync-interval-minutes` between executions (in minutes).
    @Scheduled(fixedDelayString = "#{@appProperties.syncIntervalMinutes * 60000}")
    public void scheduledSync() {
        if (appProperties.getSyncIntervalMinutes() <= 0) {
            return;
        }
        try {
            syncService.syncNewDocuments();
        } catch (Exception ignored) {
        }
    }
}
