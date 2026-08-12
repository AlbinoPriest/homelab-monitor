package dev.homelabmonitor.monitor;

import java.time.Instant;
import java.util.UUID;

public record MonitorCheckResponse(
		UUID id,
		CheckResultType result,
		Long responseTimeMillis,
		Instant checkedAt,
		String errorMessage,
		Integer httpStatus) {

	static MonitorCheckResponse from(MonitorCheck check) {
		return new MonitorCheckResponse(
				check.id(), check.result(), check.responseTimeMillis(), check.checkedAt(),
				check.errorMessage(), check.httpStatus());
	}
}
