package dev.homelabmonitor.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ClientAddressResolver {
	private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
	private final boolean trustProxyHeaders;

	ClientAddressResolver(
			@Value("${homelab-monitor.security.trust-proxy-headers:false}") boolean trustProxyHeaders) {
		this.trustProxyHeaders = trustProxyHeaders;
	}

	String resolve(HttpServletRequest request) {
		if (!trustProxyHeaders) return request.getRemoteAddr();
		String forwarded = request.getHeader("X-Real-IP");
		if (forwarded == null) return request.getRemoteAddr();
		String candidate = forwarded.trim();
		return validIpLiteral(candidate) ? candidate : request.getRemoteAddr();
	}

	private boolean validIpLiteral(String candidate) {
		if (candidate.contains(":")) {
			if (!IPV6_CHARACTERS.matcher(candidate).matches()) return false;
			try {
				return InetAddress.getByName(candidate) instanceof Inet6Address;
			} catch (UnknownHostException exception) {
				return false;
			}
		}
		String[] octets = candidate.split("\\.", -1);
		if (octets.length != 4) return false;
		for (String octet : octets) {
			if (!octet.matches("\\d{1,3}")) return false;
			if (Integer.parseInt(octet) > 255) return false;
		}
		return true;
	}
}
