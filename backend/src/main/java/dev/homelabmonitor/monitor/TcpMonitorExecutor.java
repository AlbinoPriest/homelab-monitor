package dev.homelabmonitor.monitor;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
class TcpMonitorExecutor implements MonitorExecutor {

	private final Clock clock;
	private final HostResolver hostResolver;

	TcpMonitorExecutor(Clock clock, HostResolver hostResolver) {
		this.clock = clock;
		this.hostResolver = hostResolver;
	}

	@Override
	public MonitorType type() {
		return MonitorType.TCP;
	}

	@Override
	public ExecutionResult execute(MonitorExecutionSnapshot monitor) {
		long started = System.nanoTime();
		long deadline = started + Duration.ofMillis(monitor.timeoutMillis()).toNanos();
		try {
			var addresses = hostResolver.resolve(monitor.target(), Duration.ofNanos(deadline - System.nanoTime()));
			IOException lastFailure = null;
			for (var address : addresses) {
				long remainingNanos = deadline - System.nanoTime();
				if (remainingNanos <= 0) throw new SocketTimeoutException();
				try (Socket socket = new Socket()) {
					socket.connect(new InetSocketAddress(address, monitor.port()),
							(int) Math.max(1, Duration.ofNanos(remainingNanos).toMillis()));
					return ExecutionResult.success(elapsedMillis(started), clock.instant(), null);
				} catch (ConnectException exception) {
					lastFailure = exception;
				}
			}
			if (lastFailure != null) throw lastFailure;
			throw new ConnectException();
		} catch (UnknownHostException exception) {
			return failure(CheckResultType.DNS_FAILURE, "Target hostname could not be resolved.", started);
		} catch (SocketTimeoutException | TimeoutException exception) {
			return ExecutionResult.failure(CheckResultType.TIMEOUT, null, clock.instant(), "TCP check timed out.", null);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return failure(CheckResultType.UNKNOWN_FAILURE, "TCP check was interrupted.", started);
		} catch (ConnectException exception) {
			return failure(CheckResultType.CONNECTION_REFUSED, "Connection could not be established.", started);
		} catch (IOException | RuntimeException exception) {
			return failure(CheckResultType.UNKNOWN_FAILURE, "TCP transport failed.", started);
		}
	}

	private ExecutionResult failure(CheckResultType type, String message, long started) {
		return ExecutionResult.failure(type, elapsedMillis(started), clock.instant(), message, null);
	}

	private long elapsedMillis(long started) {
		return Duration.ofNanos(System.nanoTime() - started).toMillis();
	}
}
