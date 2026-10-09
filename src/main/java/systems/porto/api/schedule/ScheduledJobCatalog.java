package systems.porto.api.schedule;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of {@link ScheduledJob} plugins loaded for the running application.
 */
public final class ScheduledJobCatalog {
    private final Map<String, ScheduledJob> jobs = new ConcurrentHashMap<>();

    public void register(final ScheduledJob job) {
        if (job == null || job.id() == null || job.id().isBlank()) {
            throw new IllegalArgumentException("Scheduled job id is required");
        }
        jobs.put(job.id().trim(), job);
    }

    public Optional<ScheduledJob> find(final String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(jobs.get(jobId.trim()));
    }

    public boolean contains(final String jobId) {
        return find(jobId).isPresent();
    }

    public void clear() {
        jobs.clear();
    }
}
