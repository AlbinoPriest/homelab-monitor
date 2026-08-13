package dev.homelabmonitor.analytics;

import java.time.Duration;
import java.util.Arrays;

public enum MetricWindow {
	ONE_HOUR("1h", Duration.ofHours(1)),
	TWENTY_FOUR_HOURS("24h", Duration.ofHours(24)),
	SEVEN_DAYS("7d", Duration.ofDays(7)),
	THIRTY_DAYS("30d", Duration.ofDays(30));

	private final String value;
	private final Duration duration;

	MetricWindow(String value, Duration duration) {
		this.value = value;
		this.duration = duration;
	}

	public String value() { return value; }
	Duration duration() { return duration; }

	static MetricWindow parse(String value) {
		return Arrays.stream(values())
				.filter(window -> window.value.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new InvalidAnalyticsRequestException(
						"Window must be one of 1h, 24h, 7d, or 30d."));
	}
}
