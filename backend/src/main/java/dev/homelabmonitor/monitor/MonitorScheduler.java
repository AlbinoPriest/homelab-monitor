package dev.homelabmonitor.monitor;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "homelab-monitor.scheduling.enabled", matchIfMissing = true)
class MonitorScheduler {

	private static final int SCAN_LIMIT = 100;
	private final MonitorRepository monitorRepository;
	private final MonitorCheckCoordinator coordinator;
	private final Clock clock;

	MonitorScheduler(MonitorRepository monitorRepository, MonitorCheckCoordinator coordinator, Clock clock) {
		this.monitorRepository = monitorRepository;
		this.coordinator = coordinator;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${homelab-monitor.scheduling.scan-delay:1000}")
	@Transactional(readOnly = true)
	void scheduleDueMonitors() {
		List<UUID> dueIds = monitorRepository
				.findByEnabledTrueAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(
						clock.instant(), PageRequest.of(0, SCAN_LIMIT))
				.stream()
				.map(Monitor::id)
				.toList();
		dueIds.forEach(coordinator::schedule);
	}
}
