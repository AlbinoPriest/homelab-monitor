package dev.homelabmonitor.auth;

import java.time.Duration;

public class LoginThrottledException extends RuntimeException {
	private final Duration retryAfter;

	LoginThrottledException(Duration retryAfter) {
		super("Too many authentication attempts. Try again later.");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() { return retryAfter; }
}
