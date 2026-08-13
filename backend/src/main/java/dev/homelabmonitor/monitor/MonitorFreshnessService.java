package dev.homelabmonitor.monitor;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonitorFreshnessService {
	private final MonitorRepository monitorRepository;
	private final MonitorStateHistoryRepository historyRepository;
	private final MonitorFreshness freshness;

	MonitorFreshnessService(MonitorRepository monitorRepository,
			MonitorStateHistoryRepository historyRepository, MonitorFreshness freshness) {
		this.monitorRepository = monitorRepository;
		this.historyRepository = historyRepository;
		this.freshness = freshness;
	}

	@Transactional
	void expireIfStale(UUID id, Instant now) {
		monitorRepository.findByIdForUpdate(id).ifPresent(monitor -> {
			if (!monitor.enabled() || !freshness.isStale(monitor, now)) return;
			MonitorStatus previous = monitor.status();
			Instant expiredAt = freshness.expiresAt(monitor);
			monitor.expireEvidence(expiredAt);
			if (previous == MonitorStatus.ONLINE || previous == MonitorStatus.DEGRADED) {
				historyRepository.save(MonitorStateHistory.create(
						monitor, previous, MonitorStatus.UNKNOWN, expiredAt, StateChangeReason.OBSERVATION_STALE));
			}
		});
	}
}
