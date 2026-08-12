package dev.homelabmonitor.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTests {
	@Test
	void limitsRepeatedAttemptsAndRecoversAfterWindow() {
		MutableClock clock = new MutableClock();
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
		for (int attempt = 0; attempt < 20; attempt++) limiter.acquire("127.0.0.1", "owner@example.com");
		assertThatThrownBy(() -> limiter.acquire("127.0.0.1", "owner@example.com"))
				.isInstanceOf(LoginThrottledException.class);
		clock.advanceSeconds(61);
		assertThatCode(() -> limiter.acquire("127.0.0.1", "owner@example.com")).doesNotThrowAnyException();
	}

	private static final class MutableClock extends Clock {
		private Instant instant = Instant.parse("2030-01-01T00:00:00Z");
		@Override public ZoneId getZone() { return ZoneId.of("UTC"); }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return instant; }
		void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
	}
}
