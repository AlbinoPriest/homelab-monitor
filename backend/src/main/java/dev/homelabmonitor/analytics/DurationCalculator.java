package dev.homelabmonitor.analytics;

import dev.homelabmonitor.analytics.AnalyticsReadRepository.Observation;
import dev.homelabmonitor.analytics.AnalyticsReadRepository.StatusEvent;
import dev.homelabmonitor.monitor.MonitorStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class DurationCalculator {

	DurationTotals calculate(
			Instant start,
			Instant end,
			List<StatusEvent> events,
			List<Observation> observations) {
		if (!start.isBefore(end)) return new DurationTotals(0, 0, 0, null);

		List<Interval> coverage = mergeCoverage(start, end, observations);
		List<StatusInterval> statuses = statusIntervals(start, end, events);
		long available = 0;
		long unavailable = 0;
		for (StatusInterval status : statuses) {
			if (!isIncluded(status.status())) continue;
			for (Interval observed : coverage) {
				long overlap = overlapMillis(status.start(), status.end(), observed.start(), observed.end());
				if (overlap == 0) continue;
				if (status.status() == MonitorStatus.OFFLINE) unavailable += overlap;
				else available += overlap;
			}
		}
		long total = Duration.between(start, end).toMillis();
		long excluded = Math.max(0, total - available - unavailable);
		return new DurationTotals(available, unavailable, excluded, percentage(available, unavailable));
	}

	private List<Interval> mergeCoverage(Instant start, Instant end, List<Observation> observations) {
		List<Interval> ranges = observations.stream()
				.map(observation -> new Interval(max(start, observation.checkedAt()), min(end, observation.validUntil())))
				.filter(interval -> interval.start().isBefore(interval.end()))
				.sorted(Comparator.comparing(Interval::start).thenComparing(Interval::end))
				.toList();
		List<Interval> merged = new ArrayList<>();
		for (Interval range : ranges) {
			if (merged.isEmpty() || range.start().isAfter(merged.getLast().end())) {
				merged.add(range);
			} else {
				Interval previous = merged.removeLast();
				merged.add(new Interval(previous.start(), max(previous.end(), range.end())));
			}
		}
		return merged;
	}

	private List<StatusInterval> statusIntervals(Instant start, Instant end, List<StatusEvent> events) {
		List<StatusInterval> intervals = new ArrayList<>();
		MonitorStatus status = null;
		Instant cursor = start;
		for (StatusEvent event : events.stream().sorted(Comparator.comparing(StatusEvent::effectiveAt)).toList()) {
			Instant effectiveAt = event.effectiveAt();
			if (!effectiveAt.isAfter(start)) {
				status = event.status();
				continue;
			}
			if (!effectiveAt.isBefore(end)) break;
			if (status != null && cursor.isBefore(effectiveAt)) {
				intervals.add(new StatusInterval(cursor, effectiveAt, status));
			}
			status = event.status();
			cursor = effectiveAt;
		}
		if (status != null && cursor.isBefore(end)) intervals.add(new StatusInterval(cursor, end, status));
		return intervals;
	}

	private boolean isIncluded(MonitorStatus status) {
		return status == MonitorStatus.ONLINE || status == MonitorStatus.DEGRADED || status == MonitorStatus.OFFLINE;
	}

	private long overlapMillis(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
		Instant start = max(aStart, bStart);
		Instant end = min(aEnd, bEnd);
		return start.isBefore(end) ? Duration.between(start, end).toMillis() : 0;
	}

	private Double percentage(long available, long unavailable) {
		long included = available + unavailable;
		return included == 0 ? null : Math.round((available * 10000.0 / included)) / 100.0;
	}

	private Instant max(Instant left, Instant right) { return left.isAfter(right) ? left : right; }
	private Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }

	record DurationTotals(long availableMillis, long unavailableMillis, long excludedMillis, Double uptimePercent) {}
	private record Interval(Instant start, Instant end) {}
	private record StatusInterval(Instant start, Instant end, MonitorStatus status) {}
}
