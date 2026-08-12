package dev.homelabmonitor.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AuthRequests {
	private AuthRequests() {}

	record Setup(
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 120) String displayName,
			@NotBlank @Size(min = 12, max = 72) String password) {}

	record Login(
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 72) String password) {}
}
