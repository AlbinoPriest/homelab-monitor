package dev.homelabmonitor.analytics;

import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(
		String window,
		Instant windowStart,
		Instant windowEnd,
		Double overallUptimePercent,
		Double averageMonitorUptimePercent,
		Double averageLatencyMillis,
		long incidentCount,
		long availableMillis,
		long downtimeMillis,
		long excludedMillis,
		boolean partial,
		List<MonitorAnalyticsSummary> monitors,
		List<MonitorAnalyticsSummary> slowestMonitors,
		List<MonitorAnalyticsSummary> leastReliableMonitors,
		List<MonitorAnalyticsSummary> mostDowntimeMonitors) {
}
