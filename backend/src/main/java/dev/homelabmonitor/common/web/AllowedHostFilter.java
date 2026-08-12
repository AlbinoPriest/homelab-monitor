package dev.homelabmonitor.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class AllowedHostFilter extends OncePerRequestFilter {

	private final Set<String> allowedHosts;

	AllowedHostFilter(@Value("${homelab-monitor.security.allowed-hosts:localhost,127.0.0.1,[::1]}") String hosts) {
		this.allowedHosts = Arrays.stream(hosts.split(","))
				.map(String::trim)
				.map(value -> value.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String host = request.getHeader("Host");
		String authority = host == null ? request.getServerName() : host;
		if (!allowedAuthority(authority)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Host header is not allowed.");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean allowedAuthority(String authority) {
		String normalized = authority.trim().toLowerCase(Locale.ROOT);
		if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("@") || normalized.contains(",")) {
			return false;
		}
		String host;
		String port = null;
		if (normalized.startsWith("[")) {
			int closing = normalized.indexOf(']');
			if (closing < 0) return false;
			host = normalized.substring(0, closing + 1);
			if (closing + 1 < normalized.length()) {
				if (normalized.charAt(closing + 1) != ':') return false;
				port = normalized.substring(closing + 2);
			}
		} else {
			int colon = normalized.indexOf(':');
			host = colon < 0 ? normalized : normalized.substring(0, colon);
			if (colon >= 0) port = normalized.substring(colon + 1);
		}
		return allowedHosts.contains(host) && validPort(port);
	}

	private boolean validPort(String port) {
		if (port == null) return true;
		if (!port.matches("\\d{1,5}")) return false;
		int value = Integer.parseInt(port);
		return value >= 1 && value <= 65535;
	}
}
