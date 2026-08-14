package dev.homelabmonitor.monitor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonitorFreshnessService {
	private final MonitorRepository monitorRepository;
	private final MonitorStateHistoryRepository historyRepository;
	private final MonitorFreshness freshness;
	private final ApplicationEventPublisher eventPublisher;

	MonitorFreshnessService(MonitorRepository monitorRepository,
			MonitorStateHistoryRepository historyRepository, MonitorFreshness freshness,
			ApplicationEventPublisher eventPublisher) {
		this.monitorRepository = monitorRepository;
		this.historyRepository = historyRepository;
		this.freshness = freshness;
		this.eventPublisher = eventPublisher;
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
			EnumSet<MonitorChange> changes = EnumSet.of(MonitorChange.FRESHNESS_EXPIRED);
			if (previous != monitor.status()) changes.add(MonitorChange.STATUS_CHANGED);
			eventPublisher.publishEvent(new MonitorChangedEvent(
					monitor.id(), expiredAt, changes, monitor.status(), null));
		});
	}
}
