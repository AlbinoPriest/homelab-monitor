package dev.homelabmonitor.monitor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class MonitorRequestValidator {
	private static final Pattern HOSTNAME = Pattern.compile(
			"(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\\.(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*\\.?");

	MonitorCommand validate(MonitorRequest request) {
		String name = request.name().trim();
		String description = normalizeOptional(request.description());
		return switch (request.type()) {
			case HTTP -> httpCommand(request, name, description);
			case TCP -> tcpCommand(request, name, description);
		};
	}

	private MonitorCommand httpCommand(MonitorRequest request, String name, String description) {
		if (request.port() != null) {
			throw new InvalidMonitorException("HTTP monitors express an optional port in the URL, not the port field.");
		}

		URI uri = parseHttpUri(request.target().trim());
		if (uri.toASCIIString().length() > 2048) {
			throw new InvalidMonitorException("HTTP target cannot exceed 2048 encoded characters.");
		}
		int expectedStatus = request.expectedHttpStatus() == null ? 200 : request.expectedHttpStatus();
		return new MonitorCommand(
				name, description, MonitorType.HTTP, uri.toASCIIString(), null, request.enabled(),
				request.intervalSeconds(), request.timeoutMillis(), request.failureThreshold(),
				request.recoveryThreshold(), request.latencyWarningMillis(), expectedStatus);
	}

	private MonitorCommand tcpCommand(MonitorRequest request, String name, String description) {
		if (request.port() == null) {
			throw new InvalidMonitorException("TCP monitors require a port.");
		}
		if (request.expectedHttpStatus() != null) {
			throw new InvalidMonitorException("TCP monitors cannot define an expected HTTP status.");
		}

		String host = request.target().trim();
		if (!validHost(host)) {
			throw new InvalidMonitorException("TCP target must be a hostname or IP address without a scheme or path.");
		}

		String normalizedHost = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
		return new MonitorCommand(
				name, description, MonitorType.TCP, normalizedHost, request.port(), request.enabled(),
				request.intervalSeconds(), request.timeoutMillis(), request.failureThreshold(),
				request.recoveryThreshold(), request.latencyWarningMillis(), null);
	}

	private boolean validHost(String host) {
		if (host.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")) return validIpv4(host);
		if (HOSTNAME.matcher(host).matches()) return true;
		String ipv6 = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
		return validIpv6Literal(ipv6);
	}

	private boolean validIpv6Literal(String value) {
		if (!value.contains(":") || value.contains("%") || !value.matches("[0-9A-Fa-f:.]+")) return false;
		int compression = value.indexOf("::");
		if (compression != value.lastIndexOf("::")) return false;
		String[] parts = value.split(":", -1);
		int units = 0;
		for (int index = 0; index < parts.length; index++) {
			String part = parts[index];
			if (part.isEmpty()) continue;
			if (part.contains(".")) {
				if (index != parts.length - 1 || !validIpv4(part)) return false;
				units += 2;
			} else {
				if (part.length() > 4 || !part.matches("[0-9A-Fa-f]+")) return false;
				units++;
			}
		}
		return compression >= 0 ? units < 8 : units == 8;
	}

	private boolean validIpv4(String host) {
		return host.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")
				&& java.util.Arrays.stream(host.split("\\.")).mapToInt(Integer::parseInt).allMatch(value -> value <= 255);
	}

	static URI parseHttpUri(String target) {
		try {
			URI uri = new URI(target);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (!(scheme.equals("http") || scheme.equals("https"))) {
				throw new InvalidMonitorException("HTTP target scheme must be http or https.");
			}
			if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()) {
				throw new InvalidMonitorException("HTTP target must be an absolute URL with a host.");
			}
			if (uri.getUserInfo() != null) {
				throw new InvalidMonitorException("HTTP target cannot contain user information.");
			}
			if (uri.getFragment() != null) {
				throw new InvalidMonitorException("HTTP target cannot contain a fragment.");
			}
			if (uri.getPort() == 0 || uri.getPort() > 65535) {
				throw new InvalidMonitorException("HTTP target port must be between 1 and 65535.");
			}
			return uri.normalize();
		} catch (URISyntaxException exception) {
			throw new InvalidMonitorException("HTTP target is not a valid URL.");
		}
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
