package dev.homelabmonitor.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MonitorMetricsResponse(
		UUID monitorId,
		String monitorName,
		String window,
		Instant windowStart,
		Instant windowEnd,
		Instant dataAvailableFrom,
		boolean partial,
		long availableMillis,
		long unavailableMillis,
		long excludedMillis,
		Double uptimePercent,
		long incidentCount,
		LatencyStatistics latency,
		List<MetricBucket> buckets) {
}
