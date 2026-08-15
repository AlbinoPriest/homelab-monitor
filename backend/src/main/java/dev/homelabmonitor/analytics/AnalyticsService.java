package dev.homelabmonitor.analytics;

import dev.homelabmonitor.analytics.AnalyticsReadRepository.MonitorRow;
import dev.homelabmonitor.analytics.DurationCalculator.DurationTotals;
import dev.homelabmonitor.monitor.MonitorNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AnalyticsService {
	private static final int BUCKET_COUNT = 24;
	private static final int RANKING_LIMIT = 5;
	private final AnalyticsReadRepository repository;
	private final Clock clock;
	private final Duration retention;
	private final boolean retentionEnabled;

	AnalyticsService(
			AnalyticsReadRepository repository,
			Clock clock,
			@Value("${homelab-monitor.retention.raw-check-days:30}") long retentionDays,
			@Value("${homelab-monitor.retention.enabled:true}") boolean retentionEnabled) {
		if (retentionDays < 1) throw new IllegalArgumentException("Raw-check retention must be at least one day.");
		this.repository = repository;
		this.clock = clock;
		this.retention = Duration.ofDays(retentionDays);
		this.retentionEnabled = retentionEnabled;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	MonitorMetricsResponse monitor(UUID id, String requestedWindow) {
		MetricWindow window = MetricWindow.parse(requestedWindow);
		Instant end = clock.instant();
		Instant requestedStart = end.minus(window.duration());
		MonitorRow monitor = repository.monitor(id).orElseThrow(() -> new MonitorNotFoundException(id));
		Instant dataStart = dataStart(monitor, requestedStart, end);
		DurationTotals measured = repository.durations(id, dataStart, end);
		Instant calculationStart = max(requestedStart, monitor.createdAt());
		DurationTotals total = accountForRequestedWindow(calculationStart, end, measured);
		return new MonitorMetricsResponse(
				monitor.id(), monitor.name(), window.value(), requestedStart, end, dataStart,
				isPartial(monitor, requestedStart, end), total.availableMillis(), total.unavailableMillis(),
				total.excludedMillis(), total.uptimePercent(),
				repository.incidentCount(id, max(requestedStart, monitor.createdAt()), end),
				repository.latency(id, dataStart, end),
				repository.buckets(id, dataStart, calculationStart, end, BUCKET_COUNT));
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	AnalyticsResponse overview(String requestedWindow) {
		MetricWindow window = MetricWindow.parse(requestedWindow);
		Instant end = clock.instant();
		Instant requestedStart = end.minus(window.duration());
		Instant queryStart = retentionEnabled ? max(requestedStart, end.minus(retention)) : requestedStart;
		List<MonitorRow> monitors = repository.monitors();
		Map<UUID, DurationTotals> measuredDurations = repository.durations(queryStart, end);
		Map<UUID, LatencyStatistics> latency = repository.latencyByMonitor(queryStart, end);
		Map<UUID, Long> incidents = repository.incidentCounts(requestedStart, end);
		List<MonitorAnalyticsSummary> summaries = monitors.stream().map(monitor -> {
			Instant dataStart = dataStart(monitor, requestedStart, end);
			DurationTotals measured = measuredDurations.getOrDefault(monitor.id(), empty(dataStart, end));
			Instant calculationStart = max(requestedStart, monitor.createdAt());
			DurationTotals total = accountForRequestedWindow(calculationStart, end, measured);
			return new MonitorAnalyticsSummary(
					monitor.id(), monitor.name(), total.uptimePercent(), total.availableMillis(),
					total.unavailableMillis(), total.excludedMillis(), incidents.getOrDefault(monitor.id(), 0L),
					latency.getOrDefault(monitor.id(), LatencyStatistics.empty()).averageMillis(),
					isPartial(monitor, requestedStart, end));
		}).toList();
		long available = summaries.stream().mapToLong(MonitorAnalyticsSummary::availableMillis).sum();
		long downtime = summaries.stream().mapToLong(MonitorAnalyticsSummary::downtimeMillis).sum();
		long excluded = summaries.stream().mapToLong(MonitorAnalyticsSummary::excludedMillis).sum();
		return new AnalyticsResponse(
				window.value(), requestedStart, end, percentage(available, downtime),
				average(summaries.stream().map(MonitorAnalyticsSummary::uptimePercent).toList()),
				repository.latency(queryStart, end).averageMillis(),
				summaries.stream().mapToLong(MonitorAnalyticsSummary::incidentCount).sum(),
				available, downtime, excluded, summaries.stream().anyMatch(MonitorAnalyticsSummary::partial), summaries,
				rank(summaries.stream().filter(summary -> summary.averageLatencyMillis() != null).toList(),
						Comparator.comparing(MonitorAnalyticsSummary::averageLatencyMillis).reversed()),
				rank(summaries.stream().filter(summary -> summary.uptimePercent() != null).toList(),
						Comparator.comparing(MonitorAnalyticsSummary::uptimePercent)),
				rank(summaries.stream().filter(summary -> summary.downtimeMillis() > 0).toList(),
						Comparator.comparingLong(MonitorAnalyticsSummary::downtimeMillis).reversed()));
	}

	private List<MonitorAnalyticsSummary> rank(
			List<MonitorAnalyticsSummary> monitors, Comparator<MonitorAnalyticsSummary> comparator) {
		return monitors.stream().sorted(comparator.thenComparing(MonitorAnalyticsSummary::monitorName))
				.limit(RANKING_LIMIT).toList();
	}

	private DurationTotals accountForRequestedWindow(
			Instant requestedStart, Instant end, DurationTotals measured) {
		long fullWindow = Duration.between(requestedStart, end).toMillis();
		return new DurationTotals(measured.availableMillis(), measured.unavailableMillis(),
				Math.max(0, fullWindow - measured.availableMillis() - measured.unavailableMillis()),
				measured.uptimePercent());
	}

	private DurationTotals empty(Instant start, Instant end) {
		return new DurationTotals(0, 0, Math.max(0, Duration.between(start, end).toMillis()), null);
	}

	private Instant dataStart(MonitorRow monitor, Instant requestedStart, Instant end) {
		Instant start = max(requestedStart, monitor.createdAt());
		return retentionEnabled ? max(start, end.minus(retention)) : start;
	}

	private boolean isPartial(MonitorRow monitor, Instant requestedStart, Instant end) {
		if (!retentionEnabled) return false;
		Instant retentionStart = end.minus(retention);
		return requestedStart.isBefore(retentionStart) && monitor.createdAt().isBefore(retentionStart);
	}

	private Double average(List<Double> values) {
		List<Double> present = values.stream().filter(java.util.Objects::nonNull).toList();
		return present.isEmpty() ? null
				: Math.round(present.stream().mapToDouble(Double::doubleValue).average().orElseThrow() * 100.0) / 100.0;
	}

	private Double percentage(long available, long downtime) {
		return available + downtime == 0 ? null
				: Math.round(available * 10000.0 / (available + downtime)) / 100.0;
	}

	private Instant max(Instant left, Instant right) { return left.isAfter(right) ? left : right; }
}
