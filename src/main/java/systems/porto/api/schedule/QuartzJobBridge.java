package systems.porto.api.schedule;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systems.porto.api.spi.HostContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quartz job class (host-only). Looks up a {@link ScheduledJob} plugin and runs it.
 */
public final class QuartzJobBridge implements Job {
    public static final String DATA_SCHEDULE_KEY = "scheduleKey";
    public static final String DATA_JOB_ID = "jobId";
    public static final String DATA_PARAMETERS = "parameters";
    public static final String CTX_CATALOG = "scheduledJobCatalog";
    public static final String CTX_HOST = "hostContext";

    private static final Logger logger = LoggerFactory.getLogger(QuartzJobBridge.class);

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();
        String scheduleKey = data.getString(DATA_SCHEDULE_KEY);
        String jobId = data.getString(DATA_JOB_ID);
        Map<String, String> parameters = parseParameters(data.getString(DATA_PARAMETERS));
        try {
            ScheduledJobCatalog catalog = (ScheduledJobCatalog) context.getScheduler().getContext().get(CTX_CATALOG);
            HostContext host = (HostContext) context.getScheduler().getContext().get(CTX_HOST);
            if (catalog == null) {
                throw new JobExecutionException("Scheduled job catalog is not bound on the Quartz scheduler");
            }
            ScheduledJob job = catalog.find(jobId)
                .orElseThrow(() -> new JobExecutionException("No scheduled-jobs plugin registered for jobId=" + jobId));
            job.execute(DefaultScheduledJobContext.open(job, scheduleKey, jobId, parameters, host));
        } catch (JobExecutionException ex) {
            throw ex;
        } catch (SchedulerException ex) {
            throw new JobExecutionException("Failed to read Quartz scheduler context", ex);
        } catch (RuntimeException ex) {
            logger.error("Scheduled job {} (schedule {}) failed", jobId, scheduleKey, ex);
            throw new JobExecutionException(ex);
        }
    }

    private static Map<String, String> parseParameters(final String packed) {
        if (packed == null || packed.isBlank()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : packed.split("\n")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }
}
