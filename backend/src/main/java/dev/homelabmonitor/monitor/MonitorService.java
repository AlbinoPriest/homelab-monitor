package dev.homelabmonitor.monitor;

import dev.homelabmonitor.common.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

	MonitorService(
			MonitorRepository monitorRepository,
			MonitorCheckRepository checkRepository,
			MonitorStateHistoryRepository historyRepository,
			MonitorRequestValidator validator,
			Clock clock) {
		this.monitorRepository = monitorRepository;
		this.checkRepository = checkRepository;
		this.historyRepository = historyRepository;
		this.validator = validator;
		this.clock = clock;
	}

	@Transactional
	MonitorResponse create(MonitorRequest request) {
		Instant now = clock.instant();
		Monitor monitor = monitorRepository.save(Monitor.create(validator.validate(request), now));
		historyRepository.save(MonitorStateHistory.create(
				monitor, null, monitor.status(), now, StateChangeReason.MONITOR_CREATED));
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
		return MonitorResponse.from(monitor);
	}

	@Transactional
	void delete(UUID id) {
		Monitor monitor = monitorRepository.findByIdForUpdate(id).orElseThrow(() -> new MonitorNotFoundException(id));
		monitorRepository.delete(monitor);
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
