package dev.homelabmonitor.monitor;

import dev.homelabmonitor.common.web.PageResponse;
import dev.homelabmonitor.incident.IncidentResolutionReason;
import dev.homelabmonitor.incident.IncidentService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonitorService {

	private final MonitorRepository monitorRepository;
	private final MonitorCheckRepository checkRepository;
	private final MonitorStateHistoryRepository historyRepository;
	private final MonitorRequestValidator validator;
	private final Clock clock;
	private final IncidentService incidentService;
	private final ApplicationEventPublisher eventPublisher;

	MonitorService(
			MonitorRepository monitorRepository,
			MonitorCheckRepository checkRepository,
			MonitorStateHistoryRepository historyRepository,
			MonitorRequestValidator validator,
			Clock clock,
			IncidentService incidentService,
			ApplicationEventPublisher eventPublisher) {
		this.monitorRepository = monitorRepository;
		this.checkRepository = checkRepository;
		this.historyRepository = historyRepository;
		this.validator = validator;
		this.clock = clock;
		this.incidentService = incidentService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	MonitorResponse create(MonitorRequest request) {
		Instant now = clock.instant();
		Monitor monitor = monitorRepository.save(Monitor.create(validator.validate(request), now));
		historyRepository.save(MonitorStateHistory.create(
				monitor, null, monitor.status(), now, StateChangeReason.MONITOR_CREATED));
		eventPublisher.publishEvent(new MonitorChangedEvent(
				monitor.id(), now, Set.of(MonitorChange.MONITOR_CREATED), monitor.status(), null));
		return MonitorResponse.from(monitor);
	}

	@Transactional(readOnly = true)
	List<MonitorResponse> list() {
		return monitorRepository.findAll(Sort.by(Sort.Direction.ASC, "name", "id")).stream()
				.map(MonitorResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	MonitorResponse get(UUID id) {
		return MonitorResponse.from(find(id));
	}

	@Transactional
	MonitorResponse update(UUID id, MonitorRequest request) {
		Monitor monitor = monitorRepository.findByIdForUpdate(id).orElseThrow(() -> new MonitorNotFoundException(id));
		MonitorStatus previous = monitor.status();
		boolean wasEnabled = monitor.enabled();
		Instant now = clock.instant();
		monitor.update(validator.validate(request), now);

		if (previous != monitor.status()) {
			StateChangeReason reason = !monitor.enabled()
					? StateChangeReason.MONITORING_PAUSED
					: wasEnabled ? StateChangeReason.CONFIGURATION_CHANGED : StateChangeReason.MONITORING_RESUMED;
			historyRepository.save(MonitorStateHistory.create(monitor, previous, monitor.status(), now, reason));
		}
		if (previous == MonitorStatus.OFFLINE && !monitor.enabled()) {
			incidentService.resolve(monitor.id(), IncidentResolutionReason.MONITORING_PAUSED, now);
		}
		EnumSet<MonitorChange> changes = EnumSet.of(MonitorChange.MONITOR_UPDATED);
		if (previous != monitor.status()) changes.add(MonitorChange.STATUS_CHANGED);
		if (previous == MonitorStatus.OFFLINE && !monitor.enabled()) changes.add(MonitorChange.INCIDENT_RESOLVED);
		eventPublisher.publishEvent(new MonitorChangedEvent(monitor.id(), now, changes, monitor.status(), null));
		return MonitorResponse.from(monitor);
	}

	@Transactional
	void delete(UUID id) {
		Monitor monitor = monitorRepository.findByIdForUpdate(id).orElseThrow(() -> new MonitorNotFoundException(id));
		monitorRepository.delete(monitor);
		eventPublisher.publishEvent(new MonitorChangedEvent(
				id, clock.instant(), Set.of(MonitorChange.MONITOR_DELETED), null, null));
	}

	@Transactional(readOnly = true)
	PageResponse<MonitorCheckResponse> checks(UUID id, int page, int size) {
		ensureExists(id);
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkedAt", "id"));
		return PageResponse.from(checkRepository.findByMonitorId(id, pageable), MonitorCheckResponse::from);
	}

	@Transactional(readOnly = true)
	PageResponse<MonitorStateHistoryResponse> history(UUID id, int page, int size) {
		ensureExists(id);
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "effectiveAt", "id"));
		return PageResponse.from(historyRepository.findByMonitorId(id, pageable), MonitorStateHistoryResponse::from);
	}

	private Monitor find(UUID id) {
		return monitorRepository.findById(id).orElseThrow(() -> new MonitorNotFoundException(id));
	}

	private void ensureExists(UUID id) {
		if (!monitorRepository.existsById(id)) {
			throw new MonitorNotFoundException(id);
		}
	}
}
