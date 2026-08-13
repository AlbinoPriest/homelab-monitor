package dev.homelabmonitor.analytics;

public record LatencyStatistics(
		long sampleCount,
		Double averageMillis,
		Long minMillis,
		Long maxMillis,
		Double medianMillis,
		Long p95Millis) {

	static LatencyStatistics empty() {
		return new LatencyStatistics(0, null, null, null, null, null);
	}
}
