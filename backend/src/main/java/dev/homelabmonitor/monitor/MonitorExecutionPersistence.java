package dev.homelabmonitor.monitor;

import dev.homelabmonitor.incident.IncidentResolutionReason;
import dev.homelabmonitor.incident.IncidentOutageReason;
import dev.homelabmonitor.incident.IncidentService;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonitorExecutionPersistence {

	private final MonitorRepository monitorRepository;
	private final MonitorCheckRepository checkRepository;
	private final MonitorStateHistoryRepository historyRepository;
	private final MonitorStateEngine stateEngine;
	private final MonitorFreshness freshness;
	private final IncidentService incidentService;
	private final ApplicationEventPublisher eventPublisher;

	MonitorExecutionPersistence(
			MonitorRepository monitorRepository,
			MonitorCheckRepository checkRepository,
			MonitorStateHistoryRepository historyRepository,
			MonitorStateEngine stateEngine,
			MonitorFreshness freshness,
			IncidentService incidentService,
			ApplicationEventPublisher eventPublisher) {
		this.monitorRepository = monitorRepository;
		this.checkRepository = checkRepository;
		this.historyRepository = historyRepository;
		this.stateEngine = stateEngine;
		this.freshness = freshness;
		this.incidentService = incidentService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	Optional<MonitorExecutionSnapshot> scheduledSnapshot(UUID id) {
		return monitorRepository.findById(id)
				.filter(Monitor::enabled)
				.map(MonitorExecutionSnapshot::from);
	}

	@Transactional(readOnly = true)
	MonitorExecutionSnapshot manualSnapshot(UUID id) {
		Monitor monitor = monitorRepository.findById(id).orElseThrow(() -> new MonitorNotFoundException(id));
		if (!monitor.enabled()) {
			throw new InvalidMonitorException("Paused monitors cannot be checked.");
		}
		return MonitorExecutionSnapshot.from(monitor);
	}

	@Transactional
	Optional<MonitorCheckResponse> complete(MonitorExecutionSnapshot snapshot, ExecutionResult result) {
		Optional<Monitor> candidate = monitorRepository.findByIdForUpdate(snapshot.id());
		if (candidate.isEmpty()) {
			return Optional.empty();
		}

		Monitor monitor = candidate.get();
		if (!monitor.enabled() || monitor.version() != snapshot.version()) {
			return Optional.empty();
		}

		expirePriorObservation(monitor, result.checkedAt());
		MonitorStatus previous = monitor.status();
		StateTransition transition = stateEngine.apply(monitor, result);
		java.time.Instant validUntil = freshness.validUntil(monitor, result.checkedAt());
		MonitorCheck check = checkRepository.save(MonitorCheck.from(monitor, result, validUntil));
		monitor.applyTransition(transition, result.checkedAt(), validUntil);
		if (transition.status() != previous) {
			historyRepository.save(MonitorStateHistory.create(
					monitor, previous, transition.status(), result.checkedAt(), transition.reason()));
		}
		if (previous != MonitorStatus.OFFLINE && transition.status() == MonitorStatus.OFFLINE) {
			incidentService.open(monitor.id(), IncidentOutageReason.valueOf(result.type().name()), result.checkedAt());
		} else if (previous == MonitorStatus.OFFLINE && transition.status() != MonitorStatus.OFFLINE) {
			incidentService.resolve(monitor.id(), IncidentResolutionReason.RECOVERED, result.checkedAt());
		}
		EnumSet<MonitorChange> changes = EnumSet.of(MonitorChange.CHECK_COMPLETED);
		if (transition.status() != previous) changes.add(MonitorChange.STATUS_CHANGED);
		if (previous != MonitorStatus.OFFLINE && transition.status() == MonitorStatus.OFFLINE) {
			changes.add(MonitorChange.INCIDENT_OPENED);
		} else if (previous == MonitorStatus.OFFLINE && transition.status() != MonitorStatus.OFFLINE) {
			changes.add(MonitorChange.INCIDENT_RESOLVED);
		}
		eventPublisher.publishEvent(new MonitorChangedEvent(
				monitor.id(), result.checkedAt(), changes, transition.status(), check.id()));
		return Optional.of(MonitorCheckResponse.from(check));
	}

	private void expirePriorObservation(Monitor monitor, java.time.Instant checkedAt) {
		if (!freshness.isStale(monitor, checkedAt)) return;
		MonitorStatus previous = monitor.status();
		java.time.Instant expiredAt = freshness.expiresAt(monitor);
		monitor.expireEvidence(expiredAt);
		if (previous == MonitorStatus.ONLINE || previous == MonitorStatus.DEGRADED) {
			historyRepository.save(MonitorStateHistory.create(
					monitor, previous, MonitorStatus.UNKNOWN, expiredAt, StateChangeReason.OBSERVATION_STALE));
		}
	}
}
