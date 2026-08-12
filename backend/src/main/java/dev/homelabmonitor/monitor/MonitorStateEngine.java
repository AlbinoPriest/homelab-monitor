package dev.homelabmonitor.monitor;

import org.springframework.stereotype.Component;

@Component
class MonitorStateEngine {

	StateTransition apply(Monitor monitor, ExecutionResult result) {
		if (result.successful()) {
			return reachable(monitor, result);
		}
		return failed(monitor);
	}

	private StateTransition reachable(Monitor monitor, ExecutionResult result) {
		MonitorStatus quality = isDegraded(monitor, result) ? MonitorStatus.DEGRADED : MonitorStatus.ONLINE;
		if (monitor.status() == MonitorStatus.OFFLINE) {
			int successes = Math.min(monitor.recoveryThreshold(), monitor.consecutiveSuccesses() + 1);
			if (successes < monitor.recoveryThreshold()) {
				return new StateTransition(MonitorStatus.OFFLINE, 0, successes, null);
			}
			return new StateTransition(quality, 0, 0, StateChangeReason.RECOVERY_THRESHOLD_REACHED);
		}

		StateChangeReason reason = null;
		if (quality != monitor.status()) {
			reason = quality == MonitorStatus.DEGRADED
					? StateChangeReason.LATENCY_THRESHOLD_EXCEEDED
					: StateChangeReason.CHECK_SUCCEEDED;
		}
		return new StateTransition(quality, 0, 0, reason);
	}

	private StateTransition failed(Monitor monitor) {
		int failures = Math.min(monitor.failureThreshold(), monitor.consecutiveFailures() + 1);
		if (monitor.status() != MonitorStatus.OFFLINE && failures >= monitor.failureThreshold()) {
			return new StateTransition(MonitorStatus.OFFLINE, failures, 0, StateChangeReason.FAILURE_THRESHOLD_REACHED);
		}
		return new StateTransition(monitor.status(), failures, 0, null);
	}

	private boolean isDegraded(Monitor monitor, ExecutionResult result) {
		return monitor.latencyWarningMillis() != null
				&& result.responseTimeMillis() != null
				&& result.responseTimeMillis() > monitor.latencyWarningMillis();
	}
}
