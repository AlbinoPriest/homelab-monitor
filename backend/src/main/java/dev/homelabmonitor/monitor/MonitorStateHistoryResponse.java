package dev.homelabmonitor.monitor;

import java.time.Instant;
import java.util.UUID;

public record MonitorStateHistoryResponse(
		UUID id,
		MonitorStatus fromStatus,
		MonitorStatus toStatus,
		Instant effectiveAt,
		StateChangeReason reason) {

	static MonitorStateHistoryResponse from(MonitorStateHistory history) {
		return new MonitorStateHistoryResponse(
				history.id(), history.fromStatus(), history.toStatus(), history.effectiveAt(), history.reason());
	}
}
