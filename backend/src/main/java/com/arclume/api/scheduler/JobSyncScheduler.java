package com.arclume.api.scheduler;

import com.arclume.api.service.JobSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.sync.enabled", havingValue = "true")
public class JobSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobSyncScheduler.class);
    private final JobSyncService jobSyncService;

    public JobSyncScheduler(JobSyncService jobSyncService) {
        this.jobSyncService = jobSyncService;
    }

    // Runs every 6 hours (4 times per day), conforming to Remotive's API rate limits
    @Scheduled(cron = "${app.jobs.sync.cron:0 0 */6 * * *}")
    public void scheduleJobSync() {
        log.info("Starting scheduled job synchronization...");
        try {
            var summary = jobSyncService.syncJobs();
            log.info("Scheduled job synchronization completed. Fetched: {}, Inserted: {}, Updated: {}, Skipped: {}, Failed: {}",
                    summary.getFetchedCount(), summary.getInsertedCount(), summary.getUpdatedCount(),
                    summary.getSkippedCount(), summary.getFailedCount());
        } catch (Exception e) {
            log.error("Scheduled job synchronization failed", e);
        }
    }
}
