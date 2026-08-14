package dev.homelabmonitor.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTests {

	@Test
	void ignoresForwardedAddressUnlessTheTrustedProxyBoundaryIsEnabled() {
		MockHttpServletRequest request = request("192.0.2.10", "198.51.100.20");

		assertThat(new ClientAddressResolver(false).resolve(request)).isEqualTo("192.0.2.10");
	}

	@Test
	void acceptsOneProxySuppliedIpLiteral() {
		assertThat(new ClientAddressResolver(true).resolve(request("172.18.0.2", "198.51.100.20")))
				.isEqualTo("198.51.100.20");
		assertThat(new ClientAddressResolver(true).resolve(request("172.18.0.2", "2001:db8::20")))
				.isEqualTo("2001:db8::20");
	}

	@Test
	void rejectsForwardedChainsAndNonIpValues() {
		assertThat(new ClientAddressResolver(true).resolve(
				request("172.18.0.2", "198.51.100.20, 203.0.113.5"))).isEqualTo("172.18.0.2");
		assertThat(new ClientAddressResolver(true).resolve(request("172.18.0.2", "client.example")))
				.isEqualTo("172.18.0.2");
		assertThat(new ClientAddressResolver(true).resolve(request("172.18.0.2", "deadbeef")))
				.isEqualTo("172.18.0.2");
		assertThat(new ClientAddressResolver(true).resolve(request("172.18.0.2", "999.51.100.20")))
				.isEqualTo("172.18.0.2");
	}

	private MockHttpServletRequest request(String remoteAddress, String forwardedAddress) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddress);
		request.addHeader("X-Real-IP", forwardedAddress);
		return request;
	}
}
