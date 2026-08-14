package dev.homelabmonitor.monitor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record MonitorChangedEvent(
		UUID monitorId,
		Instant occurredAt,
		Set<MonitorChange> changes,
		MonitorStatus status,
		UUID checkId) {

	public MonitorChangedEvent {
		changes = Set.copyOf(changes);
	}
}
