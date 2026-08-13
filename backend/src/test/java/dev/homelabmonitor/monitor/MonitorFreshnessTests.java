package dev.homelabmonitor.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MonitorFreshnessTests {
	@Test
	void expiresAtIntervalPlusTimeoutPlusSchedulerTolerance() {
		Monitor monitor = MonitorTestFixtures.monitor(MonitorType.TCP, "127.0.0.1", 9, 1, 1, null);
		MonitorFreshness freshness = new MonitorFreshness(1_000);
		monitor.applyTransition(new StateTransition(MonitorStatus.ONLINE, 0, 0, StateChangeReason.CHECK_SUCCEEDED),
				MonitorTestFixtures.NOW, freshness.validUntil(monitor, MonitorTestFixtures.NOW));

		assertThat(freshness.expiresAt(monitor)).isEqualTo(MonitorTestFixtures.NOW.plusMillis(65_500));
		assertThat(freshness.isStale(monitor, MonitorTestFixtures.NOW.plusMillis(65_499))).isFalse();
		assertThat(freshness.isStale(monitor, MonitorTestFixtures.NOW.plusMillis(65_500))).isTrue();
	}
}
