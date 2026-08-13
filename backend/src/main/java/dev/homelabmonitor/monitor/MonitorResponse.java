package dev.homelabmonitor.monitor;

import java.time.Instant;
import java.util.UUID;

public record MonitorResponse(
		UUID id,
		String name,
		String description,
		MonitorType type,
		String target,
		Integer port,
		boolean enabled,
		MonitorStatus status,
		int intervalSeconds,
		int timeoutMillis,
		int failureThreshold,
		int recoveryThreshold,
		Integer latencyWarningMillis,
		Integer expectedHttpStatus,
		int consecutiveFailures,
		int consecutiveSuccesses,
		Instant nextCheckAt,
		Instant lastCheckedAt,
		Instant createdAt,
		Instant updatedAt) {

	static MonitorResponse from(Monitor monitor) {
		return new MonitorResponse(
				monitor.id(), monitor.name(), monitor.description(), monitor.type(), monitor.target(),
				monitor.port(), monitor.enabled(), monitor.status(), monitor.intervalSeconds(),
				monitor.timeoutMillis(), monitor.failureThreshold(), monitor.recoveryThreshold(),
				monitor.latencyWarningMillis(), monitor.expectedHttpStatus(), monitor.consecutiveFailures(),
				monitor.consecutiveSuccesses(), monitor.nextCheckAt(), monitor.lastCheckedAt(),
				monitor.createdAt(), monitor.updatedAt());
	}
}
