package dev.homelabmonitor.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.homelabmonitor.analytics.AnalyticsReadRepository.MonitorRow;
import dev.homelabmonitor.analytics.DurationCalculator.DurationTotals;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTests {
	private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
	private static final UUID MONITOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	void accountsForTheRetentionLimitedPrefixAsExcluded() {
		AnalyticsReadRepository repository = mock(AnalyticsReadRepository.class);
		Instant dataStart = NOW.minus(Duration.ofDays(7));
		when(repository.monitor(MONITOR_ID)).thenReturn(Optional.of(
				new MonitorRow(MONITOR_ID, "Storage", NOW.minus(Duration.ofDays(60)))));
		when(repository.durations(MONITOR_ID, dataStart, NOW)).thenReturn(
				new DurationTotals(Duration.ofDays(1).toMillis(), Duration.ofDays(1).toMillis(),
						Duration.ofDays(5).toMillis(), 50.0));
		when(repository.latency(MONITOR_ID, dataStart, NOW)).thenReturn(LatencyStatistics.empty());
		when(repository.buckets(MONITOR_ID, dataStart, NOW.minus(Duration.ofDays(30)), NOW, 24))
				.thenReturn(List.of());
		AnalyticsService service = new AnalyticsService(
				repository, Clock.fixed(NOW, ZoneOffset.UTC), 7, true);

		MonitorMetricsResponse response = service.monitor(MONITOR_ID, "30d");

		assertThat(response.partial()).isTrue();
		assertThat(response.excludedMillis()).isEqualTo(Duration.ofDays(28).toMillis());
		assertThat(response.availableMillis() + response.unavailableMillis() + response.excludedMillis())
				.isEqualTo(Duration.ofDays(30).toMillis());
	}

	@Test
	void disablingCleanupAlsoDisablesTheAnalyticsRetentionBoundary() {
		AnalyticsReadRepository repository = mock(AnalyticsReadRepository.class);
		Instant requestedStart = NOW.minus(Duration.ofDays(30));
		when(repository.monitor(MONITOR_ID)).thenReturn(Optional.of(
				new MonitorRow(MONITOR_ID, "Storage", NOW.minus(Duration.ofDays(60)))));
		when(repository.durations(MONITOR_ID, requestedStart, NOW)).thenReturn(
				new DurationTotals(0, 0, Duration.ofDays(30).toMillis(), null));
		when(repository.latency(MONITOR_ID, requestedStart, NOW)).thenReturn(LatencyStatistics.empty());
		when(repository.buckets(MONITOR_ID, requestedStart, requestedStart, NOW, 24)).thenReturn(List.of());
		AnalyticsService service = new AnalyticsService(
				repository, Clock.fixed(NOW, ZoneOffset.UTC), 7, false);

		MonitorMetricsResponse response = service.monitor(MONITOR_ID, "30d");

		assertThat(response.partial()).isFalse();
		verify(repository).durations(MONITOR_ID, requestedStart, NOW);
	}

	@Test
	void calculationAndBucketsBeginAtMonitorCreationInsideTheWindow() {
		AnalyticsReadRepository repository = mock(AnalyticsReadRepository.class);
		Instant createdAt = NOW.minus(Duration.ofHours(12));
		when(repository.monitor(MONITOR_ID)).thenReturn(Optional.of(
				new MonitorRow(MONITOR_ID, "Storage", createdAt)));
		when(repository.durations(MONITOR_ID, createdAt, NOW)).thenReturn(
				new DurationTotals(0, 0, Duration.ofHours(12).toMillis(), null));
		when(repository.latency(MONITOR_ID, createdAt, NOW)).thenReturn(LatencyStatistics.empty());
		when(repository.buckets(MONITOR_ID, createdAt, createdAt, NOW, 24)).thenReturn(List.of());
		AnalyticsService service = new AnalyticsService(
				repository, Clock.fixed(NOW, ZoneOffset.UTC), 30, true);

		MonitorMetricsResponse response = service.monitor(MONITOR_ID, "24h");

		assertThat(response.excludedMillis()).isEqualTo(Duration.ofHours(12).toMillis());
		verify(repository).buckets(MONITOR_ID, createdAt, createdAt, NOW, 24);
	}
}
