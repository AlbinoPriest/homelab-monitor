package dev.homelabmonitor.common.web;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CsrfController {

	@GetMapping("/api/v1/csrf")
	CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
	}

	record CsrfResponse(String headerName, String parameterName, String token) {
	}
}
