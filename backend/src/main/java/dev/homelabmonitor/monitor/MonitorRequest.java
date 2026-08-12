package dev.homelabmonitor.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MonitorRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 1000) String description,
		@NotNull MonitorType type,
		@NotBlank @Size(max = 2048) String target,
		@Min(1) @Max(65535) Integer port,
		boolean enabled,
		@Min(5) @Max(86400) int intervalSeconds,
		@Min(100) @Max(30000) int timeoutMillis,
		@Min(1) @Max(100) int failureThreshold,
		@Min(1) @Max(100) int recoveryThreshold,
		@Min(1) @Max(30000) Integer latencyWarningMillis,
		@Min(100) @Max(599) Integer expectedHttpStatus) {
}
