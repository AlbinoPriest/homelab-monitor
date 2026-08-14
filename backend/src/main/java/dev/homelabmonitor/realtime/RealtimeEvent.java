package dev.homelabmonitor.realtime;

import dev.homelabmonitor.monitor.MonitorChange;
import dev.homelabmonitor.monitor.MonitorChangedEvent;
import dev.homelabmonitor.monitor.MonitorStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record RealtimeEvent(
		UUID monitorId,
		Instant occurredAt,
		List<MonitorChange> changes,
		MonitorStatus status,
		UUID checkId) {

	static RealtimeEvent from(MonitorChangedEvent event) {
		return new RealtimeEvent(event.monitorId(), event.occurredAt(),
				event.changes().stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList(),
				event.status(), event.checkId());
	}
}
