package dev.homelabmonitor.analytics;

import java.time.Instant;

public record MetricBucket(
		Instant start,
		Instant end,
		long availableMillis,
		long unavailableMillis,
		long excludedMillis,
		Double uptimePercent) {
}
