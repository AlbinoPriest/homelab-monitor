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
@Table(name = "monitor_checks")
class MonitorCheck {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "monitor_id", nullable = false)
	private Monitor monitor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CheckResultType result;

	@Column(name = "response_time_millis")
	private Long responseTimeMillis;

	@Column(name = "checked_at", nullable = false)
	private Instant checkedAt;

	@Column(name = "observation_valid_until", nullable = false)
	private Instant observationValidUntil;

	@Column(name = "error_message", length = 512)
	private String errorMessage;

	@Column(name = "http_status")
	private Integer httpStatus;

	protected MonitorCheck() {
	}

	static MonitorCheck from(Monitor monitor, ExecutionResult result, Instant observationValidUntil) {
		MonitorCheck check = new MonitorCheck();
		check.id = UUID.randomUUID();
		check.monitor = monitor;
		check.result = result.type();
		check.responseTimeMillis = result.responseTimeMillis();
		check.checkedAt = result.checkedAt();
		check.observationValidUntil = observationValidUntil;
		check.errorMessage = result.safeErrorMessage();
		check.httpStatus = result.httpStatus();
		return check;
	}

	UUID id() { return id; }
	CheckResultType result() { return result; }
	Long responseTimeMillis() { return responseTimeMillis; }
	Instant checkedAt() { return checkedAt; }
	String errorMessage() { return errorMessage; }
	Integer httpStatus() { return httpStatus; }
}
