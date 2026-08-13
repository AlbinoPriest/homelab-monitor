package dev.homelabmonitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "homelab-monitor.scheduling.enabled=false")
class IncidentMigrationTests {

	private final DataSource dataSource;

	@Autowired
	IncidentMigrationTests(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Test
	void backfillsActiveIncidentFromTheFailureThatTriggeredOffline() {
		String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
		Flyway flyway = Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.defaultSchema(schema)
				.locations("classpath:db/migration")
				.target(MigrationVersion.fromVersion("2"))
				.cleanDisabled(false)
				.load();
		try {
			flyway.migrate();
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			UUID monitorId = UUID.randomUUID();
			Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");
			Instant laterFailure = startedAt.plusSeconds(120);
			jdbc.update("""
					INSERT INTO %s.monitors (
					  id, name, type, target, port, enabled, status, interval_seconds, timeout_millis,
					  failure_threshold, recovery_threshold, consecutive_failures, consecutive_successes,
					  next_check_at, created_at, updated_at, version)
					VALUES (?, 'legacy', 'TCP', '127.0.0.1', 9, TRUE, 'OFFLINE', 86400, 30000,
					  1, 2, 1, 0, ?, ?, ?, 0)
					""".formatted(schema), monitorId, timestamp(laterFailure.plusSeconds(60)),
					timestamp(startedAt.minusSeconds(60)), timestamp(laterFailure));
			insertCheck(jdbc, schema, monitorId, "TIMEOUT", startedAt);
			jdbc.update("""
					INSERT INTO %s.monitor_state_history
					  (id, monitor_id, from_status, to_status, effective_at, reason)
					VALUES (?, ?, 'ONLINE', 'OFFLINE', ?, 'FAILURE_THRESHOLD_REACHED')
					""".formatted(schema), UUID.randomUUID(), monitorId, timestamp(startedAt));
			insertCheck(jdbc, schema, monitorId, "SUCCESS", startedAt.plusSeconds(60));
			insertCheck(jdbc, schema, monitorId, "DNS_FAILURE", laterFailure);

			Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
					.locations("classpath:db/migration").load().migrate();

			String outageReason = jdbc.queryForObject(
					"SELECT outage_reason FROM " + schema + ".incidents WHERE monitor_id = ?", String.class, monitorId);
			Instant incidentStart = jdbc.queryForObject(
					"SELECT started_at FROM " + schema + ".incidents WHERE monitor_id = ?",
					(rs, rowNum) -> rs.getTimestamp(1).toInstant(), monitorId);
			assertThat(outageReason).isEqualTo("TIMEOUT");
			assertThat(incidentStart).isEqualTo(startedAt);
			Integer missingValidity = jdbc.queryForObject(
					"SELECT COUNT(*) FROM " + schema + ".monitor_checks WHERE observation_valid_until IS NULL",
					Integer.class);
			assertThat(missingValidity).isZero();
			Instant migratedValidity = jdbc.queryForObject(
					"SELECT observation_valid_until FROM " + schema
							+ ".monitor_checks WHERE monitor_id = ? ORDER BY checked_at LIMIT 1",
					(rs, rowNum) -> rs.getTimestamp(1).toInstant(), monitorId);
			assertThat(migratedValidity).isEqualTo(startedAt);
		} finally {
			flyway.clean();
		}
	}

	private void insertCheck(JdbcTemplate jdbc, String schema, UUID monitorId, String result, Instant checkedAt) {
		jdbc.update("""
				INSERT INTO %s.monitor_checks (id, monitor_id, result, checked_at)
				VALUES (?, ?, ?, ?)
				""".formatted(schema), UUID.randomUUID(), monitorId, result, timestamp(checkedAt));
	}

	private java.sql.Timestamp timestamp(Instant instant) {
		return java.sql.Timestamp.from(instant);
	}
}
