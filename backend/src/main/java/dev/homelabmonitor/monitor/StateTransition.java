package dev.homelabmonitor.monitor;

record StateTransition(
		MonitorStatus status,
		int consecutiveFailures,
		int consecutiveSuccesses,
		StateChangeReason reason) {
}
