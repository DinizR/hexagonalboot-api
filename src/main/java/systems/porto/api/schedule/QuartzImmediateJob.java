package systems.porto.api.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.porto.api.spi.BadRequestException;
import systems.porto.api.spi.HostContext;

import java.util.Map;

final class QuartzImmediateJob {
    private static final Logger logger = LoggerFactory.getLogger(QuartzImmediateJob.class);

    private QuartzImmediateJob() {
    }

    static void run(
        final ScheduledJobCatalog catalog,
        final HostContext host,
        final JobScheduleDefinition definition
    ) {
        if (definition == null) {
            throw new BadRequestException("Schedule definition is required");
        }
        if (definition.jobId() == null || definition.jobId().isBlank()) {
            throw new BadRequestException("jobId is required");
        }
        if (host == null) {
            throw new IllegalStateException("JobScheduler host is not bound");
        }
        String jobId = definition.jobId().trim();
        ScheduledJob job = catalog.find(jobId)
            .orElseThrow(() -> new BadRequestException("Unknown jobId (no scheduled-jobs plugin): " + jobId));
        String key = requireKey(definition.scheduleKey());
        Map<String, String> parameters = definition.parameters() == null ? Map.of() : definition.parameters();
        job.execute(DefaultScheduledJobContext.open(job, key, jobId, parameters, host));
        logger.info("Ran schedule {} jobId={} immediately", key, jobId);
    }

    private static String requireKey(final String scheduleKey) {
        if (scheduleKey == null || scheduleKey.isBlank()) {
            throw new BadRequestException("scheduleKey is required");
        }
        return scheduleKey.trim();
    }
}
