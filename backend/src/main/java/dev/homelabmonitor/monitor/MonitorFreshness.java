package dev.homelabmonitor.monitor;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class MonitorFreshness {
	private final Duration schedulerTolerance;

	MonitorFreshness(@Value("${homelab-monitor.scheduling.scan-delay:1000}") long scanDelayMillis) {
		this.schedulerTolerance = Duration.ofMillis(Math.max(5_000, Math.multiplyExact(scanDelayMillis, 2)));
	}

	Instant expiresAt(Monitor monitor) {
		return monitor.observationValidUntil();
	}

	Instant validUntil(Monitor monitor, Instant checkedAt) {
		return checkedAt
				.plusSeconds(monitor.intervalSeconds())
				.plusMillis(monitor.timeoutMillis())
				.plus(schedulerTolerance);
	}

	boolean isStale(Monitor monitor, Instant now) {
		return monitor.observationValidUntil() != null && !expiresAt(monitor).isAfter(now);
	}
}
