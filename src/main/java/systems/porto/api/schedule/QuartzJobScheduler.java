package systems.porto.api.schedule;

import jakarta.annotation.PreDestroy;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import systems.porto.api.spi.BadRequestException;
import systems.porto.api.spi.HostContext;

import java.text.ParseException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Quartz-backed {@link JobScheduler}. Plugins never import this type or Quartz.
 */
@Component
public class QuartzJobScheduler implements JobScheduler {
    private static final Logger logger = LoggerFactory.getLogger(QuartzJobScheduler.class);
    private static final String GROUP = "porto-scheduled-jobs";

    private final ScheduledJobCatalog catalog = new ScheduledJobCatalog();
    private final Scheduler scheduler;
    private HostContext host;

    public QuartzJobScheduler() {
        try {
            Properties properties = new Properties();
            properties.setProperty("org.quartz.scheduler.instanceName", "porto-api");
            properties.setProperty("org.quartz.threadPool.threadCount", "4");
            properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
            this.scheduler = new StdSchedulerFactory(properties).getScheduler();
            this.scheduler.start();
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to start Quartz scheduler", ex);
        }
    }

    public void bindHost(final HostContext host) {
        this.host = host;
        try {
            scheduler.getContext().put(QuartzJobBridge.CTX_CATALOG, catalog);
            scheduler.getContext().put(QuartzJobBridge.CTX_HOST, host);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to bind Quartz scheduler context", ex);
        }
    }

    public void registerJob(final ScheduledJob job) {
        catalog.register(job);
        logger.info("Registered scheduled-jobs plugin {}", job.id());
    }

    @Override
    public boolean jobRegistered(final String jobId) {
        return catalog.contains(jobId);
    }

    @Override
    public void validateCron(final String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new BadRequestException("cronExpression is required");
        }
        try {
            CronExpression.validateExpression(cronExpression.trim());
        } catch (ParseException ex) {
            throw new BadRequestException("Invalid cronExpression: " + ex.getMessage());
        }
    }

    @Override
    public void schedule(final JobScheduleDefinition definition) {
        if (definition == null) {
            throw new BadRequestException("Schedule definition is required");
        }
        if (definition.jobId() == null || definition.jobId().isBlank()) {
            throw new BadRequestException("jobId is required");
        }
        if (!jobRegistered(definition.jobId())) {
            throw new BadRequestException("Unknown jobId (no scheduled-jobs plugin): " + definition.jobId());
        }
        validateCron(definition.cronExpression());
        String key = requireKey(definition.scheduleKey());
        try {
            JobDetail detail = JobBuilder.newJob(QuartzJobBridge.class)
                .withIdentity(jobKey(key))
                .usingJobData(QuartzJobBridge.DATA_SCHEDULE_KEY, key)
                .usingJobData(QuartzJobBridge.DATA_JOB_ID, definition.jobId().trim())
                .usingJobData(QuartzJobBridge.DATA_PARAMETERS, packParameters(definition.parameters()))
                .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(key, GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(definition.cronExpression().trim())
                    .withMisfireHandlingInstructionDoNothing())
                .build();
            scheduler.scheduleJob(detail, trigger);
            logger.info("Provisioned schedule {} jobId={} cron={}", key, definition.jobId(), definition.cronExpression());
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to provision schedule " + key, ex);
        }
    }

    @Override
    public void unschedule(final String scheduleKey) {
        String key = requireKey(scheduleKey);
        try {
            scheduler.deleteJob(jobKey(key));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to unschedule " + key, ex);
        }
    }

    @Override
    public void runNow(final JobScheduleDefinition definition) {
        QuartzImmediateJob.run(catalog, host, definition);
    }

    @Override
    public void clear() {
        catalog.clear();
        try {
            scheduler.clear();
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to clear Quartz scheduler", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (!scheduler.isShutdown()) {
                scheduler.shutdown(false);
            }
        } catch (SchedulerException ex) {
            logger.warn("Quartz shutdown failed: {}", ex.toString());
        }
    }

    private static JobKey jobKey(final String scheduleKey) {
        return JobKey.jobKey(scheduleKey, GROUP);
    }

    private static String requireKey(final String scheduleKey) {
        if (scheduleKey == null || scheduleKey.isBlank()) {
            throw new BadRequestException("scheduleKey is required");
        }
        return scheduleKey.trim();
    }

    private static String packParameters(final Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        return parameters.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
            .map(entry -> entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue()))
            .collect(Collectors.joining("\n"));
    }
}
