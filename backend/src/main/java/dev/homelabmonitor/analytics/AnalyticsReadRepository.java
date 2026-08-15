package dev.homelabmonitor.analytics;

import dev.homelabmonitor.analytics.DurationCalculator.DurationTotals;
import dev.homelabmonitor.monitor.CheckResultType;
import dev.homelabmonitor.monitor.MonitorStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AnalyticsReadRepository {
	private static final String DURATION_CTES = """
			WITH initial_events AS (
			  SELECT initial.id, initial.monitor_id, initial.to_status, initial.effective_at
			  FROM monitors monitor
			  CROSS JOIN LATERAL (
			    SELECT history.id, history.monitor_id, history.to_status, history.effective_at
			    FROM monitor_state_history history
			    WHERE history.monitor_id = monitor.id AND history.effective_at <= ?
			    ORDER BY history.effective_at DESC, history.id DESC LIMIT 1
			  ) initial
			), events AS (
			  SELECT id, monitor_id, to_status, effective_at FROM initial_events
			  UNION ALL
			  SELECT id, monitor_id, to_status, effective_at FROM monitor_state_history
			  WHERE effective_at > ? AND effective_at < ?
			), status_ranges AS (
			  SELECT monitor_id, to_status,
			         GREATEST(effective_at, ?) AS started_at,
			         LEAD(effective_at, 1, ?) OVER (PARTITION BY monitor_id ORDER BY effective_at, id) AS ended_at
			  FROM events
			), raw_coverage AS (
			  SELECT monitor_id, GREATEST(checked_at, ?) AS started_at,
			         LEAST(observation_valid_until, ?) AS ended_at
			  FROM monitor_checks
			  WHERE checked_at < ? AND observation_valid_until > ?
			), ordered_coverage AS (
			  SELECT *, MAX(ended_at) OVER (
			    PARTITION BY monitor_id ORDER BY started_at, ended_at
			    ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING) AS prior_end
			  FROM raw_coverage
			), marked_coverage AS (
			  SELECT *, SUM(CASE WHEN prior_end IS NULL OR started_at > prior_end THEN 1 ELSE 0 END)
			    OVER (PARTITION BY monitor_id ORDER BY started_at, ended_at) AS coverage_group
			  FROM ordered_coverage
			), coverage AS (
			  SELECT monitor_id, MIN(started_at) AS started_at, MAX(ended_at) AS ended_at
			  FROM marked_coverage GROUP BY monitor_id, coverage_group
			), totals AS (
			  SELECT statuses.monitor_id,
			    COALESCE(SUM(EXTRACT(EPOCH FROM LEAST(statuses.ended_at, coverage.ended_at)
			      - GREATEST(statuses.started_at, coverage.started_at)) * 1000)
			      FILTER (WHERE statuses.to_status IN ('ONLINE', 'DEGRADED')), 0)::bigint AS available_millis,
			    COALESCE(SUM(EXTRACT(EPOCH FROM LEAST(statuses.ended_at, coverage.ended_at)
			      - GREATEST(statuses.started_at, coverage.started_at)) * 1000)
			      FILTER (WHERE statuses.to_status = 'OFFLINE'), 0)::bigint AS unavailable_millis
			  FROM status_ranges statuses JOIN coverage
			    ON coverage.monitor_id = statuses.monitor_id
			   AND coverage.started_at < statuses.ended_at AND coverage.ended_at > statuses.started_at
			  GROUP BY statuses.monitor_id
			)
			""";
	private static final String MONITOR_DURATION_CTES = DURATION_CTES
			.replace("WHERE history.monitor_id = monitor.id AND history.effective_at <= ?",
					"WHERE history.monitor_id = monitor.id AND monitor.id = ? AND history.effective_at <= ?")
			.replace("WHERE effective_at > ? AND effective_at < ?",
					"WHERE monitor_id = ? AND effective_at > ? AND effective_at < ?")
			.replace("WHERE checked_at < ? AND observation_valid_until > ?",
					"WHERE monitor_id = ? AND checked_at < ? AND observation_valid_until > ?");

	private final JdbcTemplate jdbc;

	AnalyticsReadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	List<MonitorRow> monitors() {
		return jdbc.query("SELECT id, name, created_at FROM monitors ORDER BY name, id", (rs, row) ->
				new MonitorRow(rs.getObject("id", UUID.class), rs.getString("name"), instant(rs, "created_at")));
	}

	Optional<MonitorRow> monitor(UUID id) {
		return jdbc.query("SELECT id, name, created_at FROM monitors WHERE id = ?", (rs, row) ->
				new MonitorRow(rs.getObject("id", UUID.class), rs.getString("name"), instant(rs, "created_at")), id)
				.stream().findFirst();
	}

	Map<UUID, DurationTotals> durations(Instant start, Instant end) {
		return jdbc.query(DURATION_CTES + "SELECT monitor_id, available_millis, unavailable_millis FROM totals",
				rs -> {
					Map<UUID, DurationTotals> result = new HashMap<>();
					while (rs.next()) {
						long available = rs.getLong("available_millis");
						long unavailable = rs.getLong("unavailable_millis");
						result.put(rs.getObject("monitor_id", UUID.class), totals(start, end, available, unavailable));
					}
					return result;
				}, durationParameters(start, end));
	}

	DurationTotals durations(UUID monitorId, Instant start, Instant end) {
		return jdbc.query(MONITOR_DURATION_CTES + """
				SELECT available_millis, unavailable_millis FROM totals WHERE monitor_id = ?
				""", rs -> rs.next()
					? totals(start, end, rs.getLong("available_millis"), rs.getLong("unavailable_millis"))
					: totals(start, end, 0, 0), append(monitorDurationParameters(monitorId, start, end), monitorId));
	}

	List<MetricBucket> buckets(UUID monitorId, Instant dataStart, Instant requestedStart, Instant end, int count) {
		return jdbc.query(MONITOR_DURATION_CTES + """
				, bucket_defs AS (
				  SELECT bucket_number,
				    ?::timestamptz + (?::timestamptz - ?::timestamptz) * bucket_number / ? AS started_at,
				    ?::timestamptz + (?::timestamptz - ?::timestamptz) * (bucket_number + 1) / ? AS ended_at
				  FROM generate_series(0, ? - 1) AS bucket_number
				), bucket_totals AS (
				  SELECT buckets.bucket_number, buckets.started_at, buckets.ended_at,
				    COALESCE(SUM(EXTRACT(EPOCH FROM LEAST(buckets.ended_at, statuses.ended_at, coverage.ended_at)
				      - GREATEST(buckets.started_at, statuses.started_at, coverage.started_at)) * 1000)
				      FILTER (WHERE statuses.to_status IN ('ONLINE', 'DEGRADED')), 0)::bigint AS available_millis,
				    COALESCE(SUM(EXTRACT(EPOCH FROM LEAST(buckets.ended_at, statuses.ended_at, coverage.ended_at)
				      - GREATEST(buckets.started_at, statuses.started_at, coverage.started_at)) * 1000)
				      FILTER (WHERE statuses.to_status = 'OFFLINE'), 0)::bigint AS unavailable_millis
				  FROM bucket_defs buckets
				  LEFT JOIN status_ranges statuses ON statuses.monitor_id = ?
				    AND statuses.started_at < buckets.ended_at AND statuses.ended_at > buckets.started_at
				  LEFT JOIN coverage ON coverage.monitor_id = statuses.monitor_id
				    AND coverage.started_at < LEAST(buckets.ended_at, statuses.ended_at)
				    AND coverage.ended_at > GREATEST(buckets.started_at, statuses.started_at)
				  GROUP BY buckets.bucket_number, buckets.started_at, buckets.ended_at
				)
				SELECT started_at, ended_at, available_millis, unavailable_millis
				FROM bucket_totals ORDER BY bucket_number
				""", (rs, row) -> {
					Instant bucketStart = instant(rs, "started_at");
					Instant bucketEnd = instant(rs, "ended_at");
					DurationTotals total = totals(bucketStart, bucketEnd,
							rs.getLong("available_millis"), rs.getLong("unavailable_millis"));
					return new MetricBucket(bucketStart, bucketEnd, total.availableMillis(),
							total.unavailableMillis(), total.excludedMillis(), total.uptimePercent());
				}, append(monitorDurationParameters(monitorId, dataStart, end),
					timestamp(requestedStart), timestamp(end), timestamp(requestedStart), count,
					timestamp(requestedStart), timestamp(end), timestamp(requestedStart), count, count, monitorId));
	}

	Map<UUID, LatencyStatistics> latencyByMonitor(Instant start, Instant end) {
		return jdbc.query("""
				SELECT monitor_id, COUNT(*) AS samples, AVG(response_time_millis) AS average_millis,
				 MIN(response_time_millis) AS minimum_millis, MAX(response_time_millis) AS maximum_millis,
				 PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY response_time_millis) AS median_millis,
				 PERCENTILE_DISC(0.95) WITHIN GROUP (ORDER BY response_time_millis) AS p95_millis
				FROM monitor_checks WHERE checked_at >= ? AND checked_at < ?
				 AND result IN ('SUCCESS', 'UNEXPECTED_STATUS') AND response_time_millis IS NOT NULL
				GROUP BY monitor_id
				""", rs -> {
					Map<UUID, LatencyStatistics> result = new HashMap<>();
					while (rs.next()) result.put(rs.getObject("monitor_id", UUID.class), latency(rs));
					return result;
				}, timestamp(start), timestamp(end));
	}

	LatencyStatistics latency(UUID monitorId, Instant start, Instant end) {
		return jdbc.query("""
				SELECT COUNT(*) AS samples, AVG(response_time_millis) AS average_millis,
				 MIN(response_time_millis) AS minimum_millis, MAX(response_time_millis) AS maximum_millis,
				 PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY response_time_millis) AS median_millis,
				 PERCENTILE_DISC(0.95) WITHIN GROUP (ORDER BY response_time_millis) AS p95_millis
				FROM monitor_checks WHERE monitor_id = ? AND checked_at >= ? AND checked_at < ?
				 AND result IN ('SUCCESS', 'UNEXPECTED_STATUS') AND response_time_millis IS NOT NULL
				""", rs -> rs.next() ? latency(rs) : LatencyStatistics.empty(),
				monitorId, timestamp(start), timestamp(end));
	}

	LatencyStatistics latency(Instant start, Instant end) {
		return jdbc.query("""
				SELECT COUNT(*) AS samples, AVG(response_time_millis) AS average_millis,
				 MIN(response_time_millis) AS minimum_millis, MAX(response_time_millis) AS maximum_millis,
				 PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY response_time_millis) AS median_millis,
				 PERCENTILE_DISC(0.95) WITHIN GROUP (ORDER BY response_time_millis) AS p95_millis
				FROM monitor_checks WHERE checked_at >= ? AND checked_at < ?
				 AND result IN ('SUCCESS', 'UNEXPECTED_STATUS') AND response_time_millis IS NOT NULL
				""", rs -> rs.next() ? latency(rs) : LatencyStatistics.empty(), timestamp(start), timestamp(end));
	}

	long incidentCount(UUID monitorId, Instant start, Instant end) {
		Long count = jdbc.queryForObject("""
				SELECT COUNT(*) FROM incidents
				WHERE monitor_id = ? AND started_at < ? AND (ended_at IS NULL OR ended_at > ?)
				""", Long.class, monitorId, timestamp(end), timestamp(start));
		return count == null ? 0 : count;
	}

	Map<UUID, Long> incidentCounts(Instant start, Instant end) {
		return jdbc.query("""
				SELECT monitor_id, COUNT(*) AS incident_count FROM incidents
				WHERE started_at < ? AND (ended_at IS NULL OR ended_at > ?)
				GROUP BY monitor_id
				""", rs -> {
					Map<UUID, Long> counts = new HashMap<>();
					while (rs.next()) counts.put(rs.getObject("monitor_id", UUID.class), rs.getLong("incident_count"));
					return counts;
				}, timestamp(end), timestamp(start));
	}

	private Object[] durationParameters(Instant start, Instant end) {
		return new Object[] { timestamp(start), timestamp(start), timestamp(end), timestamp(start), timestamp(end),
				timestamp(start), timestamp(end), timestamp(end), timestamp(start) };
	}

	private Object[] monitorDurationParameters(UUID monitorId, Instant start, Instant end) {
		return new Object[] { monitorId, timestamp(start), monitorId, timestamp(start), timestamp(end),
				timestamp(start), timestamp(end), timestamp(start), timestamp(end), monitorId,
				timestamp(end), timestamp(start) };
	}

	private Object[] append(Object[] values, Object... additional) {
		Object[] result = java.util.Arrays.copyOf(values, values.length + additional.length);
		System.arraycopy(additional, 0, result, values.length, additional.length);
		return result;
	}

	private DurationTotals totals(Instant start, Instant end, long available, long unavailable) {
		long fullWindow = java.time.Duration.between(start, end).toMillis();
		long excluded = Math.max(0, fullWindow - available - unavailable);
		Double uptime = available + unavailable == 0 ? null
				: Math.round(available * 10000.0 / (available + unavailable)) / 100.0;
		return new DurationTotals(available, unavailable, excluded, uptime);
	}

	private LatencyStatistics latency(java.sql.ResultSet rs) throws java.sql.SQLException {
		long samples = rs.getLong("samples");
		if (samples == 0) return LatencyStatistics.empty();
		return new LatencyStatistics(samples, round(rs.getDouble("average_millis")),
				rs.getLong("minimum_millis"), rs.getLong("maximum_millis"),
				round(rs.getDouble("median_millis")), Math.round(rs.getDouble("p95_millis")));
	}

	private Double round(double value) { return Math.round(value * 100.0) / 100.0; }
	private Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		return rs.getTimestamp(column).toInstant();
	}
	private Timestamp timestamp(Instant value) { return Timestamp.from(value); }

	record MonitorRow(UUID id, String name, Instant createdAt) {}
	record StatusEvent(UUID monitorId, MonitorStatus status, Instant effectiveAt) {}
	record Observation(
			UUID monitorId,
			CheckResultType result,
			Long latencyMillis,
			Instant checkedAt,
			Instant validUntil) {}
}
