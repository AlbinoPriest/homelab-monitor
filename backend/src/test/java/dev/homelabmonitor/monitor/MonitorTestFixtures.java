package dev.homelabmonitor.monitor;

import java.time.Instant;
import java.util.UUID;

final class MonitorTestFixtures {

	static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

	private MonitorTestFixtures() {
	}

	static Monitor monitor(
			MonitorType type,
			String target,
			Integer port,
			int failureThreshold,
			int recoveryThreshold,
			Integer latencyWarningMillis) {
		MonitorCommand command = new MonitorCommand(
				"Test monitor", null, type, target, port, true, 60, 500,
				failureThreshold, recoveryThreshold, latencyWarningMillis,
				type == MonitorType.HTTP ? 200 : null);
		return Monitor.create(command, NOW);
	}

	static MonitorExecutionSnapshot snapshot(
			MonitorType type, String target, Integer port, int timeoutMillis, Integer expectedStatus) {
		return new MonitorExecutionSnapshot(
				UUID.randomUUID(), 0, type, target, port, timeoutMillis, null, expectedStatus,
				MonitorStatus.UNKNOWN, 1, 1, 0, 0);
	}
}
