package dev.homelabmonitor.incident;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
		UUID id,
		UUID monitorId,
		IncidentStatus status,
		IncidentOutageReason outageReason,
		IncidentResolutionReason resolutionReason,
		Instant startedAt,
		Instant endedAt) {
	static IncidentResponse from(Incident incident) {
		return new IncidentResponse(incident.id(), incident.monitorId(), incident.status(),
				incident.outageReason(), incident.resolutionReason(), incident.startedAt(), incident.endedAt());
	}
}
