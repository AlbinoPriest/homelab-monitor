package dev.homelabmonitor.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.homelabmonitor.analytics.AnalyticsReadRepository.Observation;
import dev.homelabmonitor.analytics.AnalyticsReadRepository.StatusEvent;
import dev.homelabmonitor.monitor.CheckResultType;
import dev.homelabmonitor.monitor.MonitorStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurationCalculatorTests {
	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
	private static final UUID MONITOR = UUID.randomUUID();

	@Test
	void intersectsAuthoritativeStatusDurationsWithMergedObservationCoverage() {
		List<StatusEvent> events = List.of(
				new StatusEvent(MONITOR, MonitorStatus.ONLINE, START),
				new StatusEvent(MONITOR, MonitorStatus.OFFLINE, START.plusSeconds(30)));
		List<Observation> observations = List.of(
				observation(0, 20), observation(10, 20), observation(25, 50));

		var result = new DurationCalculator().calculate(START, START.plusSeconds(60), events, observations);

		assertThat(result.availableMillis()).isEqualTo(25_000);
		assertThat(result.unavailableMillis()).isEqualTo(20_000);
		assertThat(result.excludedMillis()).isEqualTo(15_000);
		assertThat(result.uptimePercent()).isEqualTo(55.56);
	}

	@Test
	void excludesUnknownAndPausedDurationsEvenWhenChecksExist() {
		List<StatusEvent> events = List.of(
				new StatusEvent(MONITOR, MonitorStatus.UNKNOWN, START),
				new StatusEvent(MONITOR, MonitorStatus.PAUSED, START.plusSeconds(30)));

		var result = new DurationCalculator().calculate(
				START, START.plusSeconds(60), events, List.of(observation(0, 60)));

		assertThat(result.availableMillis()).isZero();
		assertThat(result.unavailableMillis()).isZero();
		assertThat(result.excludedMillis()).isEqualTo(60_000);
		assertThat(result.uptimePercent()).isNull();
	}

	private Observation observation(long startSeconds, long endSeconds) {
		return new Observation(MONITOR, CheckResultType.SUCCESS, 10L,
				START.plusSeconds(startSeconds), START.plusSeconds(endSeconds));
	}
}
