package dev.homelabmonitor.monitor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.homelabmonitor.TestcontainersConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = "homelab-monitor.scheduling.enabled=false")
class MonitorApiIntegrationTests {

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final MonitorRepository monitorRepository;
	private final MonitorCheckCoordinator coordinator;

	@Autowired
	MonitorApiIntegrationTests(
			MockMvc mockMvc,
			ObjectMapper objectMapper,
			MonitorRepository monitorRepository,
			MonitorCheckCoordinator coordinator) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.monitorRepository = monitorRepository;
		this.coordinator = coordinator;
	}

	@BeforeEach
	void clearMonitors() {
		monitorRepository.deleteAll();
	}

	@Test
	void createsUpdatesPausesAndRecordsStateHistory() throws Exception {
		UUID id = createMonitor(httpRequest(false, "http://127.0.0.1:8080/health", 1));

		mockMvc.perform(get("/api/v1/monitors/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAUSED"));

		mockMvc.perform(put("/api/v1/monitors/{id}", id)
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(httpRequest(true, "http://127.0.0.1:8080/health", 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UNKNOWN"));

		mockMvc.perform(put("/api/v1/monitors/{id}", id)
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(httpRequest(false, "http://127.0.0.1:8080/health", 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAUSED"));

		mockMvc.perform(get("/api/v1/monitors/{id}/history", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].reason").value("MONITORING_PAUSED"));
	}

	@Test
	void executesTcpCheckAndPersistsOnlineTransition() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			UUID id = createMonitor(tcpRequest(server.getLocalPort(), 1));

			mockMvc.perform(post("/api/v1/monitors/{id}/checks", id).with(csrf()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.result").value("SUCCESS"));

			mockMvc.perform(get("/api/v1/monitors/{id}", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("ONLINE"));

			mockMvc.perform(get("/api/v1/monitors/{id}/checks", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(1));
		}
	}

	@Test
	void appliesFailureThresholdAndRejectsInvalidTypeFields() throws Exception {
		int closedPort;
		try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			closedPort = reservation.getLocalPort();
		}
		UUID id = createMonitor(tcpRequest(closedPort, 2));

		mockMvc.perform(post("/api/v1/monitors/{id}/checks", id).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("CONNECTION_REFUSED"));
		mockMvc.perform(get("/api/v1/monitors/{id}", id))
				.andExpect(jsonPath("$.status").value("UNKNOWN"));

		mockMvc.perform(post("/api/v1/monitors/{id}/checks", id).with(csrf()))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/monitors/{id}", id))
				.andExpect(jsonPath("$.status").value("OFFLINE"));

		String invalid = httpRequest(true, "http://127.0.0.1", 1).replace("\"port\":null", "\"port\":8080");
		mockMvc.perform(post("/api/v1/monitors")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(invalid))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid monitor"));
	}

	@Test
	void requiresCsrfForMutationEndpoints() throws Exception {
		mockMvc.perform(post("/api/v1/monitors")
					.contentType(MediaType.APPLICATION_JSON)
					.content(httpRequest(false, "http://127.0.0.1", 1)))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsMalformedTcpTargetsAndOutOfRangePages() throws Exception {
		String invalidTarget = tcpRequest(8080, 1).replace("127.0.0.1", "host?query");
		mockMvc.perform(post("/api/v1/monitors").with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(invalidTarget))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid monitor"));

		UUID id = createMonitor(httpRequest(false, "http://127.0.0.1", 1));
		mockMvc.perform(get("/api/v1/monitors/{id}/checks", id).param("size", "101"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void exposesCsrfTokenForApiClients() throws Exception {
		mockMvc.perform(get("/api/v1/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void rejectsUntrustedHostHeaderBeforeApiAccess() throws Exception {
		mockMvc.perform(get("/api/v1/csrf").header("Host", "rebound.attacker.example"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void executesScheduledWorkOnTheBoundedWorkerPool() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			UUID id = createMonitor(tcpRequest(server.getLocalPort(), 1));
			org.assertj.core.api.Assertions.assertThat(coordinator.schedule(id)).isTrue();

			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
			long totalChecks = 0;
			do {
				MvcResult result = mockMvc.perform(get("/api/v1/monitors/{id}/checks", id))
						.andExpect(status().isOk())
						.andReturn();
				totalChecks = objectMapper.readTree(result.getResponse().getContentAsString())
						.get("totalElements").asLong();
				if (totalChecks == 0) {
					Thread.sleep(20);
				}
			} while (totalChecks == 0 && System.nanoTime() < deadline);

			org.assertj.core.api.Assertions.assertThat(totalChecks).isEqualTo(1);
		}
	}

	@Test
	void deletingMonitorRemovesItsHistoryAndChecks() throws Exception {
		UUID id = createMonitor(httpRequest(false, "http://127.0.0.1", 1));

		mockMvc.perform(delete("/api/v1/monitors/{id}", id).with(csrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/monitors/{id}", id))
				.andExpect(status().isNotFound());
	}

	@Test
	void metadataOnlyUpdatePreservesEstablishedState() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			String request = tcpRequest(server.getLocalPort(), 1);
			UUID id = createMonitor(request);
			mockMvc.perform(post("/api/v1/monitors/{id}/checks", id).with(csrf()))
					.andExpect(status().isOk());

			mockMvc.perform(put("/api/v1/monitors/{id}", id).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(request.replace("TCP test", "Renamed monitor")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value("Renamed monitor"))
					.andExpect(jsonPath("$.status").value("ONLINE"));
		}
	}

	@Test
	void discardsResultWhenConfigurationChangesDuringActiveCheck() throws Exception {
		CountDownLatch requestStarted = new CountDownLatch(1);
		CountDownLatch releaseResponse = new CountDownLatch(1);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/slow", exchange -> {
			requestStarted.countDown();
			try {
				releaseResponse.await(2, TimeUnit.SECONDS);
				exchange.sendResponseHeaders(200, -1);
				exchange.close();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		});
		server.start();
		String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";
		UUID id = createMonitor(httpRequest(true, target, 1));

		try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
			Future<MvcResult> activeCheck = caller.submit(() -> mockMvc.perform(
					post("/api/v1/monitors/{id}/checks", id).with(csrf())).andReturn());
			org.assertj.core.api.Assertions.assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue();

			mockMvc.perform(put("/api/v1/monitors/{id}", id)
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(httpRequest(false, target, 1)))
					.andExpect(status().isOk());
			releaseResponse.countDown();

			org.assertj.core.api.Assertions.assertThat(activeCheck.get(2, TimeUnit.SECONDS).getResponse().getStatus())
					.isEqualTo(400);
			mockMvc.perform(get("/api/v1/monitors/{id}/checks", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(0));
		} finally {
			releaseResponse.countDown();
			server.stop(0);
		}
	}

	@Test
	void retainsActiveResultAcrossMetadataOnlyUpdate() throws Exception {
		CountDownLatch requestStarted = new CountDownLatch(1);
		CountDownLatch releaseResponse = new CountDownLatch(1);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/slow", exchange -> {
			requestStarted.countDown();
			try {
				releaseResponse.await(2, TimeUnit.SECONDS);
				exchange.sendResponseHeaders(200, -1);
				exchange.close();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		});
		server.start();
		String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";
		String request = httpRequest(true, target, 1);
		UUID id = createMonitor(request);

		try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
			Future<MvcResult> activeCheck = caller.submit(() -> mockMvc.perform(
					post("/api/v1/monitors/{id}/checks", id).with(csrf())).andReturn());
			org.assertj.core.api.Assertions.assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue();
			mockMvc.perform(put("/api/v1/monitors/{id}", id).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(request.replace("HTTP test", "Renamed HTTP monitor")))
					.andExpect(status().isOk());
			releaseResponse.countDown();
			org.assertj.core.api.Assertions.assertThat(activeCheck.get(2, TimeUnit.SECONDS).getResponse().getStatus())
					.isEqualTo(200);
			mockMvc.perform(get("/api/v1/monitors/{id}/checks", id))
					.andExpect(jsonPath("$.totalElements").value(1));
		} finally {
			releaseResponse.countDown();
			server.stop(0);
		}
	}

	@Test
	void discardsResultWhenMonitorIsDeletedDuringActiveCheck() throws Exception {
		CountDownLatch requestStarted = new CountDownLatch(1);
		CountDownLatch releaseResponse = new CountDownLatch(1);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/slow", exchange -> {
			requestStarted.countDown();
			try {
				releaseResponse.await(2, TimeUnit.SECONDS);
				exchange.sendResponseHeaders(200, -1);
				exchange.close();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		});
		server.start();
		String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";
		UUID id = createMonitor(httpRequest(true, target, 1));

		try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
			Future<MvcResult> activeCheck = caller.submit(() -> mockMvc.perform(
					post("/api/v1/monitors/{id}/checks", id).with(csrf())).andReturn());
			org.assertj.core.api.Assertions.assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue();
			mockMvc.perform(delete("/api/v1/monitors/{id}", id).with(csrf()))
					.andExpect(status().isNoContent());
			releaseResponse.countDown();
			org.assertj.core.api.Assertions.assertThat(activeCheck.get(2, TimeUnit.SECONDS).getResponse().getStatus())
					.isEqualTo(400);
			mockMvc.perform(get("/api/v1/monitors/{id}", id)).andExpect(status().isNotFound());
		} finally {
			releaseResponse.countDown();
			server.stop(0);
		}
	}

	private UUID createMonitor(String request) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/monitors")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").stringValue());
	}

	private String httpRequest(boolean enabled, String target, int failureThreshold) {
		return """
				{
				  "name":"HTTP test",
				  "description":null,
				  "type":"HTTP",
				  "target":"%s",
				  "port":null,
				  "enabled":%s,
				  "intervalSeconds":60,
				  "timeoutMillis":500,
				  "failureThreshold":%d,
				  "recoveryThreshold":1,
				  "latencyWarningMillis":200,
				  "expectedHttpStatus":200
				}
				""".formatted(target, enabled, failureThreshold);
	}

	private String tcpRequest(int port, int failureThreshold) {
		return """
				{
				  "name":"TCP test",
				  "description":null,
				  "type":"TCP",
				  "target":"127.0.0.1",
				  "port":%d,
				  "enabled":true,
				  "intervalSeconds":60,
				  "timeoutMillis":500,
				  "failureThreshold":%d,
				  "recoveryThreshold":1,
				  "latencyWarningMillis":200,
				  "expectedHttpStatus":null
				}
				""".formatted(port, failureThreshold);
	}
}
