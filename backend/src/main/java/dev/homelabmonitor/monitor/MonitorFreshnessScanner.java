package dev.homelabmonitor.monitor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "homelab-monitor.scheduling.enabled", matchIfMissing = true)
class MonitorFreshnessScanner {
	private static final List<MonitorStatus> EXPIRABLE = List.of(
			MonitorStatus.UNKNOWN, MonitorStatus.ONLINE, MonitorStatus.DEGRADED, MonitorStatus.OFFLINE);
	private final MonitorRepository monitorRepository;
	private final MonitorCheckCoordinator coordinator;
	private final MonitorFreshnessService freshnessService;
	private final Clock clock;

	MonitorFreshnessScanner(MonitorRepository monitorRepository, MonitorCheckCoordinator coordinator,
			MonitorFreshnessService freshnessService, Clock clock) {
		this.monitorRepository = monitorRepository;
		this.coordinator = coordinator;
		this.freshnessService = freshnessService;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${homelab-monitor.scheduling.scan-delay:1000}")
	void expireStaleObservations() {
		Instant now = clock.instant();
		monitorRepository.findByEnabledTrueAndStatusInAndObservationValidUntilLessThanEqual(EXPIRABLE, now).stream()
				.map(Monitor::id)
				.forEach(id -> expireClaimed(id, now));
	}

	private void expireClaimed(java.util.UUID id, Instant now) {
		if (!coordinator.claimForFreshness(id)) return;
		try {
			freshnessService.expireIfStale(id, now);
		} finally {
			coordinator.releaseFreshnessClaim(id);
		}
	}
}
