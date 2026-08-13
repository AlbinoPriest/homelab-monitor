package dev.homelabmonitor.monitor;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "homelab-monitor.retention.enabled", matchIfMissing = true)
class MonitorCheckRetention {
	private final MonitorCheckRepository repository;
	private final Clock clock;
	private final long retentionDays;
	private final int batchSize;
	private final int maxBatchesPerRun;

	MonitorCheckRetention(
			MonitorCheckRepository repository,
			Clock clock,
			@Value("${homelab-monitor.retention.raw-check-days:30}") long retentionDays,
			@Value("${homelab-monitor.retention.batch-size:1000}") int batchSize,
			@Value("${homelab-monitor.retention.max-batches-per-run:10}") int maxBatchesPerRun) {
		if (retentionDays < 1) throw new IllegalArgumentException("Raw-check retention must be at least one day.");
		if (batchSize < 1 || batchSize > 10_000) {
			throw new IllegalArgumentException("Retention batch size must be between 1 and 10000.");
		}
		if (maxBatchesPerRun < 1 || maxBatchesPerRun > 100) {
			throw new IllegalArgumentException("Retention batches per run must be between 1 and 100.");
		}
		this.repository = repository;
		this.clock = clock;
		this.retentionDays = retentionDays;
		this.batchSize = batchSize;
		this.maxBatchesPerRun = maxBatchesPerRun;
	}

	@Scheduled(
			initialDelayString = "${homelab-monitor.retention.initial-delay:60000}",
			fixedDelayString = "${homelab-monitor.retention.cleanup-delay:86400000}")
	void deleteExpiredChecks() {
		Instant cutoff = clock.instant().minus(java.time.Duration.ofDays(retentionDays));
		for (int batch = 0; batch < maxBatchesPerRun; batch++) {
			if (repository.deleteRetentionBatch(cutoff, batchSize) < batchSize) break;
		}
	}
}
