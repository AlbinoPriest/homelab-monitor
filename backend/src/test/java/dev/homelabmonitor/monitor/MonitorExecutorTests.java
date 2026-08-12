package dev.homelabmonitor.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitorExecutorTests {

	private HttpServer httpServer;
	private ExecutorService httpWorkers;
	private HttpMonitorExecutor httpExecutor;

	@BeforeEach
	void startHttpServer() throws IOException {
		httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		httpWorkers = Executors.newCachedThreadPool();
		httpServer.setExecutor(httpWorkers);
		httpServer.createContext("/ok", exchange -> respond(exchange, 204));
		httpServer.createContext("/unexpected", exchange -> respond(exchange, 503));
		httpServer.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().add("Location", "/ok");
			respond(exchange, 302);
		});
		httpServer.createContext("/invalid-redirect", exchange -> {
			exchange.getResponseHeaders().add("Location", "file:///etc/passwd");
			respond(exchange, 302);
		});
		httpServer.createContext("/slow", exchange -> {
			try {
				Thread.sleep(300);
				respond(exchange, 200);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		});
		httpServer.start();
		httpExecutor = new HttpMonitorExecutor(Clock.fixed(MonitorTestFixtures.NOW, ZoneOffset.UTC));
	}

	@AfterEach
	void stopHttpServer() {
		httpServer.stop(0);
		httpWorkers.shutdownNow();
	}

	@Test
	void executesExpectedStatusAndRevalidatesRedirects() {
		String base = "http://127.0.0.1:" + httpServer.getAddress().getPort();

		ExecutionResult direct = httpExecutor.execute(
				MonitorTestFixtures.snapshot(MonitorType.HTTP, base + "/ok", null, 500, 204));
		ExecutionResult redirect = httpExecutor.execute(
				MonitorTestFixtures.snapshot(MonitorType.HTTP, base + "/redirect", null, 500, 204));
		ExecutionResult invalidRedirect = httpExecutor.execute(
				MonitorTestFixtures.snapshot(MonitorType.HTTP, base + "/invalid-redirect", null, 500, 200));

		assertThat(direct.type()).isEqualTo(CheckResultType.SUCCESS);
		assertThat(redirect.type()).isEqualTo(CheckResultType.SUCCESS);
		assertThat(invalidRedirect.type()).isEqualTo(CheckResultType.INVALID_TARGET);
	}

	@Test
	void classifiesUnexpectedStatusAndTimeout() {
		String base = "http://127.0.0.1:" + httpServer.getAddress().getPort();

		ExecutionResult unexpected = httpExecutor.execute(
				MonitorTestFixtures.snapshot(MonitorType.HTTP, base + "/unexpected", null, 500, 200));
		ExecutionResult timeout = httpExecutor.execute(
				MonitorTestFixtures.snapshot(MonitorType.HTTP, base + "/slow", null, 100, 200));

		assertThat(unexpected.type()).isEqualTo(CheckResultType.UNEXPECTED_STATUS);
		assertThat(unexpected.httpStatus()).isEqualTo(503);
		assertThat(timeout.type()).isEqualTo(CheckResultType.TIMEOUT);
	}

	@Test
	void executesTcpAndClassifiesRefusedConnection() throws IOException {
		TcpMonitorExecutor tcpExecutor = new TcpMonitorExecutor(
				Clock.fixed(MonitorTestFixtures.NOW, ZoneOffset.UTC), new HostResolver(Runnable::run));
		try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
			ExecutionResult success = tcpExecutor.execute(MonitorTestFixtures.snapshot(
					MonitorType.TCP, java.net.InetAddress.getLoopbackAddress().getHostAddress(), server.getLocalPort(), 500, null));
			assertThat(success.type()).isEqualTo(CheckResultType.SUCCESS);
		}

		int closedPort;
		try (ServerSocket reservation = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
			closedPort = reservation.getLocalPort();
		}
		ExecutionResult refused = tcpExecutor.execute(MonitorTestFixtures.snapshot(
				MonitorType.TCP, java.net.InetAddress.getLoopbackAddress().getHostAddress(), closedPort, 500, null));
		assertThat(refused.type()).isEqualTo(CheckResultType.CONNECTION_REFUSED);
	}

	private void respond(HttpExchange exchange, int status) throws IOException {
		exchange.sendResponseHeaders(status, -1);
		exchange.close();
	}
}
