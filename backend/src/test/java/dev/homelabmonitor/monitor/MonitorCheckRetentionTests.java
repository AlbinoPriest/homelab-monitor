package dev.homelabmonitor.monitor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MonitorCheckRetentionTests {
	@Test
	void defaultCleanupCapacityExceedsMaximumSupportedIngestion() {
		long runsPerDay = Duration.ofDays(1).toMillis()
				/ MonitorCheckRetention.DEFAULT_CLEANUP_DELAY_MILLIS;
		long cleanupCapacity = runsPerDay * MonitorCheckRetention.DEFAULT_BATCH_SIZE
				* MonitorCheckRetention.DEFAULT_MAX_BATCHES_PER_RUN;
		long maximumScheduledChecks = MonitorWorkerConfiguration.SUPPORTED_MONITORS
				* Duration.ofDays(1).toSeconds()
				/ MonitorRequest.MIN_INTERVAL_SECONDS;
		long maximumManualChecks = MonitorCheckCoordinator.MAX_MANUAL_CHECK_STARTS_PER_SECOND
				* Duration.ofDays(1).toSeconds();

		org.assertj.core.api.Assertions.assertThat(cleanupCapacity)
				.isGreaterThan(maximumScheduledChecks + maximumManualChecks);
	}

	@Test
	void deletesExpiredChecksInBoundedBatches() {
		MonitorCheckRepository repository = mock(MonitorCheckRepository.class);
		Instant now = Instant.parse("2026-08-13T12:00:00Z");
		Instant cutoff = now.minus(java.time.Duration.ofDays(30));
		when(repository.deleteRetentionBatch(cutoff, 1_000)).thenReturn(1_000, 12);
		MonitorCheckRetention retention = new MonitorCheckRetention(
				repository, Clock.fixed(now, ZoneOffset.UTC), 30, 1_000, 10);

		retention.deleteExpiredChecks();

		verify(repository, org.mockito.Mockito.times(2)).deleteRetentionBatch(cutoff, 1_000);
	}

	@Test
	void stopsAtTheConfiguredPerRunBatchLimit() {
		MonitorCheckRepository repository = mock(MonitorCheckRepository.class);
		Instant now = Instant.parse("2026-08-13T12:00:00Z");
		Instant cutoff = now.minus(java.time.Duration.ofDays(30));
		when(repository.deleteRetentionBatch(cutoff, 1_000)).thenReturn(1_000);
		MonitorCheckRetention retention = new MonitorCheckRetention(
				repository, Clock.fixed(now, ZoneOffset.UTC), 30, 1_000, 3);

		retention.deleteExpiredChecks();

		verify(repository, org.mockito.Mockito.times(3)).deleteRetentionBatch(cutoff, 1_000);
	}
}
