package dev.homelabmonitor.monitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.springframework.stereotype.Component;

@Component
class HttpMonitorExecutor implements MonitorExecutor {

	private static final int MAX_REDIRECTS = 3;
	private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
	private final Clock clock;
	private final HttpClient client;

	HttpMonitorExecutor(Clock clock) {
		this.clock = clock;
		this.client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(30))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	@Override
	public MonitorType type() {
		return MonitorType.HTTP;
	}

	@Override
	public ExecutionResult execute(MonitorExecutionSnapshot monitor) {
		long started = System.nanoTime();
		long deadline = started + Duration.ofMillis(monitor.timeoutMillis()).toNanos();

		try {
			URI current = MonitorRequestValidator.parseHttpUri(monitor.target());
			for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
				long remainingNanos = deadline - System.nanoTime();
				if (remainingNanos <= 0) {
					return timeout();
				}

				HttpRequest request = HttpRequest.newBuilder(current)
						.timeout(Duration.ofNanos(remainingNanos))
						.header("Accept", "*/*")
						.header("User-Agent", "HomeLab-Monitor/1.0.0")
						.GET()
						.build();
				HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
				try (InputStream ignored = response.body()) {
					int status = response.statusCode();
					if (REDIRECT_STATUSES.contains(status)) {
						if (redirects == MAX_REDIRECTS) {
							return failure(CheckResultType.INVALID_TARGET,
									"HTTP redirect limit exceeded.", status, started);
						}
						String location = response.headers().firstValue("location")
								.orElseThrow(() -> new InvalidMonitorException("HTTP redirect did not include a location."));
						current = resolveRedirect(current, location);
						continue;
					}

					long latency = elapsedMillis(started);
					if (status != monitor.expectedHttpStatus()) {
						return ExecutionResult.failure(
								CheckResultType.UNEXPECTED_STATUS, latency, clock.instant(),
								"Expected HTTP " + monitor.expectedHttpStatus() + " but received " + status + ".", status);
					}
					return ExecutionResult.success(latency, clock.instant(), status);
				}
			}
			return failure(CheckResultType.UNKNOWN_FAILURE, "HTTP check failed.", null, started);
		} catch (InvalidMonitorException exception) {
			return failure(CheckResultType.INVALID_TARGET, exception.getMessage(), null, started);
		} catch (HttpTimeoutException exception) {
			return timeout();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return failure(CheckResultType.UNKNOWN_FAILURE, "HTTP check was interrupted.", null, started);
		} catch (IOException exception) {
			return classify(exception, started);
		} catch (RuntimeException exception) {
			return failure(CheckResultType.UNKNOWN_FAILURE, "HTTP check failed safely.", null, started);
		}
	}

	private ExecutionResult classify(IOException exception, long started) {
		if (hasCause(exception, UnknownHostException.class)
				|| hasCause(exception, UnresolvedAddressException.class)) {
			return failure(CheckResultType.DNS_FAILURE, "Target hostname could not be resolved.", null, started);
		}
		if (hasCause(exception, SSLException.class)) {
			return failure(CheckResultType.TLS_ERROR, "TLS negotiation failed.", null, started);
		}
		if (hasCause(exception, ConnectException.class)) {
			return failure(CheckResultType.CONNECTION_REFUSED, "Connection could not be established.", null, started);
		}
		return failure(CheckResultType.UNKNOWN_FAILURE, "HTTP transport failed.", null, started);
	}

	private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return true;
			}
		}
		return false;
	}

	private URI resolveRedirect(URI current, String location) {
		try {
			return MonitorRequestValidator.parseHttpUri(current.resolve(location).toString());
		} catch (IllegalArgumentException exception) {
			throw new InvalidMonitorException("HTTP redirect location is invalid.");
		}
	}

	private ExecutionResult timeout() {
		return ExecutionResult.failure(CheckResultType.TIMEOUT, null, clock.instant(), "HTTP check timed out.", null);
	}

	private ExecutionResult failure(
			CheckResultType type, String message, Integer status, long started) {
		return ExecutionResult.failure(type, elapsedMillis(started), clock.instant(), message, status);
	}

	private long elapsedMillis(long started) {
		return Duration.ofNanos(System.nanoTime() - started).toMillis();
	}
}
