package dev.homelabmonitor.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
class Incident {
	@Id private UUID id;
	@Column(name = "monitor_id", nullable = false) private UUID monitorId;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private IncidentStatus status;
	@Enumerated(EnumType.STRING) @Column(name = "outage_reason", nullable = false, length = 32)
	private IncidentOutageReason outageReason;
	@Enumerated(EnumType.STRING) @Column(name = "resolution_reason", length = 32)
	private IncidentResolutionReason resolutionReason;
	@Column(name = "started_at", nullable = false) private Instant startedAt;
	@Column(name = "ended_at") private Instant endedAt;

	protected Incident() {}

	static Incident start(UUID monitorId, IncidentOutageReason reason, Instant startedAt) {
		Incident incident = new Incident();
		incident.id = UUID.randomUUID();
		incident.monitorId = monitorId;
		incident.status = IncidentStatus.ACTIVE;
		incident.outageReason = reason;
		incident.startedAt = startedAt;
		return incident;
	}

	void resolve(IncidentResolutionReason reason, Instant endedAt) {
		if (status == IncidentStatus.RESOLVED) return;
		status = IncidentStatus.RESOLVED;
		resolutionReason = reason;
		this.endedAt = endedAt;
	}

	UUID id() { return id; }
	UUID monitorId() { return monitorId; }
	IncidentStatus status() { return status; }
	IncidentOutageReason outageReason() { return outageReason; }
	IncidentResolutionReason resolutionReason() { return resolutionReason; }
	Instant startedAt() { return startedAt; }
	Instant endedAt() { return endedAt; }
}
