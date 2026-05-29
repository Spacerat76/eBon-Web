package de.spacerat76.ebon.service;

import de.spacerat76.ebon.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class PaperlessSyncSchedulerTest {

    @Test
    void scheduledSync_calls_syncService_whenIntervalPositive() {
        PaperlessSyncService mockService = mock(PaperlessSyncService.class);
        AppProperties props = new AppProperties();
        props.setSyncIntervalMinutes(15);
        PaperlessSyncScheduler scheduler = new PaperlessSyncScheduler(mockService, props);
        scheduler.scheduledSync();
        verify(mockService, times(1)).syncNewDocuments();
    }

    @Test
    void scheduledSync_skips_whenIntervalZeroOrNegative() {
        PaperlessSyncService mockService = mock(PaperlessSyncService.class);
        AppProperties props = new AppProperties();
        props.setSyncIntervalMinutes(0);
        PaperlessSyncScheduler scheduler = new PaperlessSyncScheduler(mockService, props);
        scheduler.scheduledSync();
        verifyNoInteractions(mockService);
    }
}
