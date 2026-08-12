package dev.homelabmonitor.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monitor_state_history")
class MonitorStateHistory {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "monitor_id", nullable = false)
	private Monitor monitor;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 16)
	private MonitorStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 16)
	private MonitorStatus toStatus;

	@Column(name = "effective_at", nullable = false)
	private Instant effectiveAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 64)
	private StateChangeReason reason;

	protected MonitorStateHistory() {
	}

	static MonitorStateHistory create(
			Monitor monitor,
			MonitorStatus fromStatus,
			MonitorStatus toStatus,
			Instant effectiveAt,
			StateChangeReason reason) {
		MonitorStateHistory history = new MonitorStateHistory();
		history.id = UUID.randomUUID();
		history.monitor = monitor;
		history.fromStatus = fromStatus;
		history.toStatus = toStatus;
		history.effectiveAt = effectiveAt;
		history.reason = reason;
		return history;
	}

	UUID id() { return id; }
	MonitorStatus fromStatus() { return fromStatus; }
	MonitorStatus toStatus() { return toStatus; }
	Instant effectiveAt() { return effectiveAt; }
	StateChangeReason reason() { return reason; }
}
