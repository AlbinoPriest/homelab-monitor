package dev.homelabmonitor.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class LoginAttemptLimiter {
	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final int GLOBAL_LIMIT = 100;
	private static final int SOURCE_LIMIT = 20;
	private static final int ACCOUNT_LIMIT = 20;
	private final Clock clock;
	private final ArrayDeque<Instant> global = new ArrayDeque<>();
	private final Map<String, ArrayDeque<Instant>> sources = new HashMap<>();
	private final Map<String, ArrayDeque<Instant>> accounts = new HashMap<>();

	LoginAttemptLimiter(Clock clock) { this.clock = clock; }

	synchronized void acquire(String source, String email) {
		Instant now = clock.instant();
		Instant cutoff = now.minus(WINDOW);
		prune(global, cutoff);
		prune(sources, cutoff);
		prune(accounts, cutoff);
		ArrayDeque<Instant> sourceAttempts = attempts(sources, source, cutoff);
		ArrayDeque<Instant> accountAttempts = attempts(accounts, email.trim().toLowerCase(Locale.ROOT), cutoff);
		if (global.size() >= GLOBAL_LIMIT || sourceAttempts.size() >= SOURCE_LIMIT
				|| accountAttempts.size() >= ACCOUNT_LIMIT) throw new LoginThrottledException(WINDOW);
		global.addLast(now);
		sourceAttempts.addLast(now);
		accountAttempts.addLast(now);
	}

	private ArrayDeque<Instant> attempts(Map<String, ArrayDeque<Instant>> attempts, String key, Instant cutoff) {
		ArrayDeque<Instant> values = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
		prune(values, cutoff);
		return values;
	}

	private void prune(ArrayDeque<Instant> attempts, Instant cutoff) {
		while (!attempts.isEmpty() && !attempts.getFirst().isAfter(cutoff)) attempts.removeFirst();
	}

	private void prune(Map<String, ArrayDeque<Instant>> attempts, Instant cutoff) {
		attempts.values().forEach(values -> prune(values, cutoff));
		attempts.values().removeIf(ArrayDeque::isEmpty);
	}
}
