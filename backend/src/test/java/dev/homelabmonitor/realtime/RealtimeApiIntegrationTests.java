package dev.homelabmonitor.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.homelabmonitor.TestcontainersConfiguration;
import dev.homelabmonitor.monitor.MonitorChange;
import dev.homelabmonitor.monitor.MonitorChangedEvent;
import dev.homelabmonitor.monitor.MonitorStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"homelab-monitor.scheduling.enabled=false",
		"homelab-monitor.realtime.connection-timeout=30000"
})
class RealtimeApiIntegrationTests {
	private final MockMvc mockMvc;
	private final RealtimeBroker broker;
	private final tools.jackson.databind.ObjectMapper objectMapper;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactions;

	@Autowired
	RealtimeApiIntegrationTests(MockMvc mockMvc, RealtimeBroker broker,
			tools.jackson.databind.ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher,
			PlatformTransactionManager transactionManager) {
		this.mockMvc = mockMvc;
		this.broker = broker;
		this.objectMapper = objectMapper;
		this.eventPublisher = eventPublisher;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@AfterEach
	void closeStreams() {
		broker.close();
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/events"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void opensAnAuthenticatedEventStream() throws Exception {
		mockMvc.perform(get("/api/v1/events").with(user("owner").roles("OWNER")))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted());
	}

	@Test
	void publishesCommittedMonitorChangesToAnOpenStream() throws Exception {
		MvcResult stream = mockMvc.perform(get("/api/v1/events").with(user("owner").roles("OWNER")))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andReturn();

		MvcResult created = mockMvc.perform(post("/api/v1/monitors")
				.with(user("owner").roles("OWNER"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Realtime target","description":"","type":"TCP","target":"127.0.0.1",
						 "port":9,"enabled":true,"intervalSeconds":60,"timeoutMillis":1000,
						 "failureThreshold":1,"recoveryThreshold":1}
						"""))
				.andExpect(status().isCreated())
				.andReturn();

		String monitorId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
		assertThat(awaitStreamContains(stream, monitorId))
				.contains("MONITOR_CREATED")
				.contains(monitorId);
	}

	@Test
	void rollsBackWithoutPublishingAndClosesStreamsWhenTheirSessionEnds() throws Exception {
		org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
		MvcResult stream = mockMvc.perform(get("/api/v1/events")
				.session(session).with(user("owner").roles("OWNER")))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andReturn();
		UUID rolledBackMonitor = UUID.randomUUID();
		transactions.executeWithoutResult(status -> {
			eventPublisher.publishEvent(new MonitorChangedEvent(rolledBackMonitor, Instant.now(),
					Set.of(MonitorChange.MONITOR_CREATED), MonitorStatus.UNKNOWN, null));
			status.setRollbackOnly();
		});
		LockSupport.parkNanos(java.time.Duration.ofMillis(100).toNanos());
		assertThat(stream.getResponse().getContentAsString()).doesNotContain(rolledBackMonitor.toString());

		mockMvc.perform(post("/api/v1/auth/logout")
				.session(session).with(user("owner").roles("OWNER")).with(csrf()))
				.andExpect(status().isNoContent());
		assertThat(broker.connectionCount()).isZero();
	}

	private String awaitStreamContains(MvcResult stream, String expected) throws Exception {
		long deadline = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
		String body;
		do {
			body = stream.getResponse().getContentAsString();
			if (body.contains(expected)) return body;
			LockSupport.parkNanos(java.time.Duration.ofMillis(10).toNanos());
		} while (System.nanoTime() < deadline);
		return body;
	}
}
