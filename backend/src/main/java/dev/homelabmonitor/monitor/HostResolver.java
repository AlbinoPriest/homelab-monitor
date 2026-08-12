package dev.homelabmonitor.monitor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

@Component
class HostResolver {

	private final Executor dnsExecutor;

	HostResolver(@Qualifier("monitorDnsExecutor") Executor dnsExecutor) {
		this.dnsExecutor = dnsExecutor;
	}

	InetAddress[] resolve(String host, Duration timeout)
			throws UnknownHostException, TimeoutException, InterruptedException {
		CompletableFuture<InetAddress[]> future;
		try {
			future = CompletableFuture.supplyAsync(() -> lookup(host), dnsExecutor);
		} catch (TaskRejectedException exception) {
			throw new TimeoutException("DNS resolver is busy.");
		}
		try {
			return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
		} catch (ExecutionException exception) {
			if (exception.getCause() instanceof UnknownHostRuntimeException unknown) {
				throw unknown.cause;
			}
			throw new UnknownHostException(host);
		} finally {
			future.cancel(true);
		}
	}

	private InetAddress[] lookup(String host) {
		try {
			return InetAddress.getAllByName(host);
		} catch (UnknownHostException exception) {
			throw new UnknownHostRuntimeException(exception);
		}
	}

	private static final class UnknownHostRuntimeException extends RuntimeException {
		private final UnknownHostException cause;

		private UnknownHostRuntimeException(UnknownHostException cause) {
			this.cause = cause;
		}
	}
}
