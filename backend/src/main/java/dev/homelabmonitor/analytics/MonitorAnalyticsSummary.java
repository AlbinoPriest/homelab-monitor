package dev.homelabmonitor.analytics;

import java.util.UUID;

public record MonitorAnalyticsSummary(
		UUID monitorId,
		String monitorName,
		Double uptimePercent,
		long availableMillis,
		long downtimeMillis,
		long excludedMillis,
		long incidentCount,
		Double averageLatencyMillis,
		boolean partial) {
	static MonitorAnalyticsSummary from(MonitorMetricsResponse metrics) {
		return new MonitorAnalyticsSummary(
				metrics.monitorId(), metrics.monitorName(), metrics.uptimePercent(),
				metrics.availableMillis(), metrics.unavailableMillis(), metrics.excludedMillis(),
				metrics.incidentCount(), metrics.latency().averageMillis(), metrics.partial());
	}
}
