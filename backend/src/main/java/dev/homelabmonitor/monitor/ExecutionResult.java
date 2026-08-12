package dev.homelabmonitor.monitor;

import java.time.Instant;

record ExecutionResult(
		CheckResultType type,
		Long responseTimeMillis,
		Instant checkedAt,
		String safeErrorMessage,
		Integer httpStatus) {

	ExecutionResult {
		if (safeErrorMessage != null && safeErrorMessage.length() > 512) {
			safeErrorMessage = safeErrorMessage.substring(0, 512);
		}
	}

	static ExecutionResult success(long latencyMillis, Instant checkedAt, Integer httpStatus) {
		return new ExecutionResult(CheckResultType.SUCCESS, latencyMillis, checkedAt, null, httpStatus);
	}

	static ExecutionResult failure(
			CheckResultType type, Long latencyMillis, Instant checkedAt, String message, Integer httpStatus) {
		return new ExecutionResult(type, latencyMillis, checkedAt, message, httpStatus);
	}

	boolean successful() {
		return type == CheckResultType.SUCCESS;
	}
}
