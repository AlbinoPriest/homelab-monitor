package dev.homelabmonitor.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MonitorWorkerConfigurationTests {
	@Test
	void scheduledPoolCoversTheSupportedTimeoutBoundEnvelope() {
		long waves = (MonitorWorkerConfiguration.SUPPORTED_MONITORS
				+ MonitorWorkerConfiguration.SCHEDULED_WORKERS - 1L)
				/ MonitorWorkerConfiguration.SCHEDULED_WORKERS;
		Duration worstCaseDrain = Duration.ofMillis(MonitorRequest.MAX_TIMEOUT_MILLIS).multipliedBy(waves);

		assertThat(worstCaseDrain).isLessThanOrEqualTo(Duration.ofSeconds(MonitorRequest.MIN_INTERVAL_SECONDS));
		assertThat(MonitorWorkerConfiguration.SCHEDULED_WORKERS
				+ MonitorWorkerConfiguration.SCHEDULED_QUEUE_CAPACITY)
				.isEqualTo(MonitorWorkerConfiguration.SUPPORTED_MONITORS);
		assertThat(MonitorWorkerConfiguration.DNS_WORKERS
				+ MonitorWorkerConfiguration.DNS_QUEUE_CAPACITY)
				.isEqualTo(MonitorWorkerConfiguration.SCHEDULED_WORKERS
						+ MonitorWorkerConfiguration.MANUAL_WORKERS);
	}
}
