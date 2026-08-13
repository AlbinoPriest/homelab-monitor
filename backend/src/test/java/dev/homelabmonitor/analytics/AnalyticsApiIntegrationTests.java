package dev.homelabmonitor.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.homelabmonitor.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"homelab-monitor.scheduling.enabled=false",
		"homelab-monitor.retention.enabled=false"
})
class AnalyticsApiIntegrationTests {
	private final MockMvc mockMvc;
	private final JdbcTemplate jdbc;
	private UUID monitorId;

	@Autowired
	AnalyticsApiIntegrationTests(MockMvc mockMvc, JdbcTemplate jdbc) {
		this.mockMvc = mockMvc;
		this.jdbc = jdbc;
	}

	@BeforeEach
	void seedDurations() {
		jdbc.update("DELETE FROM incidents");
		jdbc.update("DELETE FROM monitor_state_history");
		jdbc.update("DELETE FROM monitor_checks");
		jdbc.update("DELETE FROM monitors");
		monitorId = UUID.randomUUID();
		Instant now = Instant.now();
		Instant created = now.minusSeconds(3_600);
		Instant online = now.minusSeconds(2_400);
		Instant offline = now.minusSeconds(1_200);
		jdbc.update("""
				INSERT INTO monitors (
				 id, name, type, target, port, enabled, status, interval_seconds, timeout_millis,
				 failure_threshold, recovery_threshold, consecutive_failures, consecutive_successes,
				 next_check_at, last_checked_at, observation_valid_until, created_at, updated_at, version)
				VALUES (?, 'Analytics monitor', 'TCP', '127.0.0.1', 9, TRUE, 'OFFLINE', 60, 500,
				 1, 1, 1, 0, ?, ?, ?, ?, ?, 0)
				""", monitorId, ts(now.plusSeconds(60)), ts(offline), ts(now), ts(created), ts(offline));
		state(null, "UNKNOWN", created, "MONITOR_CREATED");
		state("UNKNOWN", "ONLINE", online, "CHECK_SUCCEEDED");
		state("ONLINE", "OFFLINE", offline, "FAILURE_THRESHOLD_REACHED");
		check("SUCCESS", 10L, online, offline);
		check("SUCCESS", 30L, online.plusSeconds(600), offline);
		check("UNEXPECTED_STATUS", 50L, online.plusSeconds(900), offline);
		check("CONNECTION_REFUSED", 5L, offline, now);
		jdbc.update("""
				INSERT INTO incidents (id, monitor_id, status, outage_reason, started_at)
				VALUES (?, ?, 'ACTIVE', 'CONNECTION_REFUSED', ?)
				""", UUID.randomUUID(), monitorId, ts(offline));
	}

	@Test
	void returnsDurationUptimeLatencyIncidentsAndBuckets() throws Exception {
		mockMvc.perform(get("/api/v1/monitors/{id}/metrics", monitorId)
				.param("window", "1h").with(user("owner").roles("OWNER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uptimePercent").value(50.0))
				.andExpect(jsonPath("$.incidentCount").value(1))
				.andExpect(jsonPath("$.latency.sampleCount").value(3))
				.andExpect(jsonPath("$.latency.averageMillis").value(30.0))
				.andExpect(jsonPath("$.latency.medianMillis").value(30.0))
				.andExpect(jsonPath("$.latency.p95Millis").value(50))
				.andExpect(jsonPath("$.buckets.length()").value(24));

		mockMvc.perform(get("/api/v1/analytics").param("window", "1h")
				.with(user("owner").roles("OWNER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overallUptimePercent").value(50.0))
				.andExpect(jsonPath("$.monitors[0].monitorName").value("Analytics monitor"));
	}

	@Test
	void rejectsUnknownWindowAndProtectsAnalytics() throws Exception {
		mockMvc.perform(get("/api/v1/analytics").param("window", "90d")
				.with(user("owner").roles("OWNER")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/analytics")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/monitors/{id}/metrics", monitorId)).andExpect(status().isUnauthorized());
	}

	private void state(String from, String to, Instant at, String reason) {
		jdbc.update("""
				INSERT INTO monitor_state_history (id, monitor_id, from_status, to_status, effective_at, reason)
				VALUES (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), monitorId, from, to, ts(at), reason);
	}

	private void check(String result, Long latency, Instant at, Instant validUntil) {
		jdbc.update("""
				INSERT INTO monitor_checks
				 (id, monitor_id, result, response_time_millis, checked_at, observation_valid_until)
				VALUES (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), monitorId, result, latency, ts(at), ts(validUntil));
	}

	private Timestamp ts(Instant value) { return Timestamp.from(value); }
}
