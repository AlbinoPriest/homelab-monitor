package dev.homelabmonitor.incident;

import dev.homelabmonitor.common.web.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {
	private final IncidentRepository repository;

	IncidentService(IncidentRepository repository) { this.repository = repository; }

	@Transactional
	public void open(UUID monitorId, IncidentOutageReason reason, Instant startedAt) {
		if (repository.findByMonitorIdAndStatus(monitorId, IncidentStatus.ACTIVE).isEmpty()) {
			repository.save(Incident.start(monitorId, reason, startedAt));
		}
	}

	@Transactional
	public void resolve(UUID monitorId, IncidentResolutionReason reason, Instant endedAt) {
		repository.findByMonitorIdAndStatus(monitorId, IncidentStatus.ACTIVE)
				.ifPresent(incident -> incident.resolve(reason, endedAt));
	}

	@Transactional(readOnly = true)
	PageResponse<IncidentResponse> list(UUID monitorId, IncidentStatus status, int page, int size) {
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "id"));
		Page<Incident> incidents;
		if (monitorId != null && status != null) incidents = repository.findByMonitorIdAndStatus(monitorId, status, pageable);
		else if (monitorId != null) incidents = repository.findByMonitorId(monitorId, pageable);
		else if (status != null) incidents = repository.findByStatus(status, pageable);
		else incidents = repository.findAll(pageable);
		return PageResponse.from(incidents, IncidentResponse::from);
	}
}
