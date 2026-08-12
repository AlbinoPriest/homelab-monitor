package dev.homelabmonitor.monitor;

import java.util.UUID;

record MonitorExecutionSnapshot(
		UUID id,
		long version,
		MonitorType type,
		String target,
		Integer port,
		int timeoutMillis,
		Integer latencyWarningMillis,
		Integer expectedHttpStatus,
		MonitorStatus status,
		int failureThreshold,
		int recoveryThreshold,
		int consecutiveFailures,
		int consecutiveSuccesses) {

	static MonitorExecutionSnapshot from(Monitor monitor) {
		return new MonitorExecutionSnapshot(
				monitor.id(), monitor.version(), monitor.type(), monitor.target(), monitor.port(),
				monitor.timeoutMillis(), monitor.latencyWarningMillis(), monitor.expectedHttpStatus(),
				monitor.status(), monitor.failureThreshold(), monitor.recoveryThreshold(),
				monitor.consecutiveFailures(), monitor.consecutiveSuccesses());
	}
}
