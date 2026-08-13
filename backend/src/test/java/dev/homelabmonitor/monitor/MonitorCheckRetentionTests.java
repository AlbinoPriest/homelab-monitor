package dev.homelabmonitor.monitor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MonitorCheckRetentionTests {
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
