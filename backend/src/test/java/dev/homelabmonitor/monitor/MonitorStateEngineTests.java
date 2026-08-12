package dev.homelabmonitor.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MonitorStateEngineTests {

	private final MonitorStateEngine engine = new MonitorStateEngine();

	@Test
	void establishesOnlineAndDegradedQualityImmediately() {
		Monitor monitor = MonitorTestFixtures.monitor(MonitorType.HTTP, "http://localhost", null, 3, 2, 100);

		StateTransition online = engine.apply(monitor, ExecutionResult.success(80, MonitorTestFixtures.NOW, 200));
		assertThat(online.status()).isEqualTo(MonitorStatus.ONLINE);
		monitor.applyTransition(online, MonitorTestFixtures.NOW);

		StateTransition degraded = engine.apply(monitor, ExecutionResult.success(101, MonitorTestFixtures.NOW, 200));
		assertThat(degraded.status()).isEqualTo(MonitorStatus.DEGRADED);
		assertThat(degraded.reason()).isEqualTo(StateChangeReason.LATENCY_THRESHOLD_EXCEEDED);
	}

	@Test
	void requiresConsecutiveFailuresAndReachableResultsResetProgress() {
		Monitor monitor = MonitorTestFixtures.monitor(MonitorType.TCP, "localhost", 80, 2, 2, null);
		ExecutionResult failure = ExecutionResult.failure(
				CheckResultType.CONNECTION_REFUSED, 1L, MonitorTestFixtures.NOW, "Connection failed.", null);

		StateTransition firstFailure = engine.apply(monitor, failure);
		assertThat(firstFailure.status()).isEqualTo(MonitorStatus.UNKNOWN);
		assertThat(firstFailure.consecutiveFailures()).isEqualTo(1);
		monitor.applyTransition(firstFailure, MonitorTestFixtures.NOW);

		StateTransition success = engine.apply(monitor, ExecutionResult.success(1, MonitorTestFixtures.NOW, null));
		assertThat(success.status()).isEqualTo(MonitorStatus.ONLINE);
		assertThat(success.consecutiveFailures()).isZero();
		monitor.applyTransition(success, MonitorTestFixtures.NOW);

		StateTransition failureAfterReset = engine.apply(monitor, failure);
		assertThat(failureAfterReset.status()).isEqualTo(MonitorStatus.ONLINE);
		assertThat(failureAfterReset.consecutiveFailures()).isEqualTo(1);
	}

	@Test
	void recoversOfflineMonitorOnlyAfterThresholdUsingLatestReachableQuality() {
		Monitor monitor = MonitorTestFixtures.monitor(MonitorType.HTTP, "http://localhost", null, 1, 2, 100);
		StateTransition offline = engine.apply(monitor, ExecutionResult.failure(
				CheckResultType.TIMEOUT, null, MonitorTestFixtures.NOW, "Timed out.", null));
		monitor.applyTransition(offline, MonitorTestFixtures.NOW);

		StateTransition firstRecovery = engine.apply(monitor, ExecutionResult.success(50, MonitorTestFixtures.NOW, 200));
		assertThat(firstRecovery.status()).isEqualTo(MonitorStatus.OFFLINE);
		assertThat(firstRecovery.consecutiveSuccesses()).isEqualTo(1);
		monitor.applyTransition(firstRecovery, MonitorTestFixtures.NOW);

		StateTransition recovered = engine.apply(monitor, ExecutionResult.success(150, MonitorTestFixtures.NOW, 200));
		assertThat(recovered.status()).isEqualTo(MonitorStatus.DEGRADED);
		assertThat(recovered.reason()).isEqualTo(StateChangeReason.RECOVERY_THRESHOLD_REACHED);
		assertThat(recovered.consecutiveSuccesses()).isZero();
	}

	@Test
	void failureDuringRecoveryResetsRecoveryProgress() {
		Monitor monitor = MonitorTestFixtures.monitor(MonitorType.TCP, "localhost", 80, 1, 2, null);
		ExecutionResult failure = ExecutionResult.failure(
				CheckResultType.CONNECTION_REFUSED, 1L, MonitorTestFixtures.NOW, "Connection failed.", null);
		monitor.applyTransition(engine.apply(monitor, failure), MonitorTestFixtures.NOW);
		monitor.applyTransition(
				engine.apply(monitor, ExecutionResult.success(1, MonitorTestFixtures.NOW, null)), MonitorTestFixtures.NOW);

		StateTransition reset = engine.apply(monitor, failure);

		assertThat(reset.status()).isEqualTo(MonitorStatus.OFFLINE);
		assertThat(reset.consecutiveSuccesses()).isZero();
	}
}
