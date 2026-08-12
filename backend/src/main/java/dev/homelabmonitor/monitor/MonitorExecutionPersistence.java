package dev.homelabmonitor.monitor;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonitorExecutionPersistence {

	private final MonitorRepository monitorRepository;
	private final MonitorCheckRepository checkRepository;
	private final MonitorStateHistoryRepository historyRepository;
	private final MonitorStateEngine stateEngine;

	MonitorExecutionPersistence(
			MonitorRepository monitorRepository,
			MonitorCheckRepository checkRepository,
			MonitorStateHistoryRepository historyRepository,
			MonitorStateEngine stateEngine) {
		this.monitorRepository = monitorRepository;
		this.checkRepository = checkRepository;
		this.historyRepository = historyRepository;
		this.stateEngine = stateEngine;
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

		MonitorStatus previous = monitor.status();
		StateTransition transition = stateEngine.apply(monitor, result);
		MonitorCheck check = checkRepository.save(MonitorCheck.from(monitor, result));
		monitor.applyTransition(transition, result.checkedAt());
		if (transition.status() != previous) {
			historyRepository.save(MonitorStateHistory.create(
					monitor, previous, transition.status(), result.checkedAt(), transition.reason()));
		}
		return Optional.of(MonitorCheckResponse.from(check));
	}
}
