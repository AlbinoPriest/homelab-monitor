package dev.homelabmonitor.monitor;

record MonitorCommand(
		String name,
		String description,
		MonitorType type,
		String target,
		Integer port,
		boolean enabled,
		int intervalSeconds,
		int timeoutMillis,
		int failureThreshold,
		int recoveryThreshold,
		Integer latencyWarningMillis,
		Integer expectedHttpStatus) {
}
