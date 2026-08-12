package dev.homelabmonitor.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.OptimisticLock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monitors")
class Monitor {

	@Id
	private UUID id;

	@Column(nullable = false, length = 120)
	@OptimisticLock(excluded = true)
	private String name;

	@Column(length = 1000)
	@OptimisticLock(excluded = true)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MonitorType type;

	@Column(nullable = false, length = 2048)
	private String target;

	private Integer port;

	@Column(nullable = false)
	private boolean enabled;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MonitorStatus status;

	@Column(name = "interval_seconds", nullable = false)
	private int intervalSeconds;

	@Column(name = "timeout_millis", nullable = false)
	private int timeoutMillis;

	@Column(name = "failure_threshold", nullable = false)
	private int failureThreshold;

	@Column(name = "recovery_threshold", nullable = false)
	private int recoveryThreshold;

	@Column(name = "latency_warning_millis")
	private Integer latencyWarningMillis;

	@Column(name = "expected_http_status")
	private Integer expectedHttpStatus;

	@Column(name = "consecutive_failures", nullable = false)
	private int consecutiveFailures;

	@Column(name = "consecutive_successes", nullable = false)
	private int consecutiveSuccesses;

	@Column(name = "next_check_at")
	private Instant nextCheckAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	@OptimisticLock(excluded = true)
	private Instant updatedAt;

	@Version
	private long version;

	protected Monitor() {
	}

	static Monitor create(MonitorCommand command, Instant now) {
		Monitor monitor = new Monitor();
		monitor.id = UUID.randomUUID();
		monitor.applyConfiguration(command);
		monitor.status = command.enabled() ? MonitorStatus.UNKNOWN : MonitorStatus.PAUSED;
		monitor.nextCheckAt = command.enabled() ? now : null;
		monitor.createdAt = now;
		monitor.updatedAt = now;
		return monitor;
	}

	void update(MonitorCommand command, Instant now) {
		boolean wasEnabled = enabled;
		boolean executionChanged = executionConfigurationChanged(command);
		applyConfiguration(command);
		if (executionChanged) {
			consecutiveFailures = 0;
			consecutiveSuccesses = 0;
			status = command.enabled() ? MonitorStatus.UNKNOWN : MonitorStatus.PAUSED;
			nextCheckAt = command.enabled() ? now : null;
		}
		updatedAt = now;
		if (!wasEnabled && command.enabled()) {
			status = MonitorStatus.UNKNOWN;
		}
	}

	private boolean executionConfigurationChanged(MonitorCommand command) {
		return type != command.type() || !target.equals(command.target())
				|| !java.util.Objects.equals(port, command.port()) || enabled != command.enabled()
				|| intervalSeconds != command.intervalSeconds() || timeoutMillis != command.timeoutMillis()
				|| failureThreshold != command.failureThreshold() || recoveryThreshold != command.recoveryThreshold()
				|| !java.util.Objects.equals(latencyWarningMillis, command.latencyWarningMillis())
				|| !java.util.Objects.equals(expectedHttpStatus, command.expectedHttpStatus());
	}

	private void applyConfiguration(MonitorCommand command) {
		name = command.name();
		description = command.description();
		type = command.type();
		target = command.target();
		port = command.port();
		enabled = command.enabled();
		intervalSeconds = command.intervalSeconds();
		timeoutMillis = command.timeoutMillis();
		failureThreshold = command.failureThreshold();
		recoveryThreshold = command.recoveryThreshold();
		latencyWarningMillis = command.latencyWarningMillis();
		expectedHttpStatus = command.expectedHttpStatus();
	}

	void applyTransition(StateTransition transition, Instant checkedAt) {
		status = transition.status();
		consecutiveFailures = transition.consecutiveFailures();
		consecutiveSuccesses = transition.consecutiveSuccesses();
		nextCheckAt = checkedAt.plusSeconds(intervalSeconds);
		updatedAt = checkedAt;
	}

	UUID id() { return id; }
	String name() { return name; }
	String description() { return description; }
	MonitorType type() { return type; }
	String target() { return target; }
	Integer port() { return port; }
	boolean enabled() { return enabled; }
	MonitorStatus status() { return status; }
	int intervalSeconds() { return intervalSeconds; }
	int timeoutMillis() { return timeoutMillis; }
	int failureThreshold() { return failureThreshold; }
	int recoveryThreshold() { return recoveryThreshold; }
	Integer latencyWarningMillis() { return latencyWarningMillis; }
	Integer expectedHttpStatus() { return expectedHttpStatus; }
	int consecutiveFailures() { return consecutiveFailures; }
	int consecutiveSuccesses() { return consecutiveSuccesses; }
	Instant nextCheckAt() { return nextCheckAt; }
	Instant createdAt() { return createdAt; }
	Instant updatedAt() { return updatedAt; }
	long version() { return version; }
}
