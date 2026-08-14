package dev.homelabmonitor.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.homelabmonitor.incident.IncidentService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class MonitorExecutionPersistenceTests {

	@Test
	void reconcilesExpiredObservationAtItsDeadlineBeforeApplyingNewResult() {
		Monitor monitor = MonitorTestFixtures.monitor(MonitorType.TCP, "127.0.0.1", 9, 1, 1, null);
		MonitorFreshness freshness = new MonitorFreshness(1_000);
		Instant oldExpiry = freshness.validUntil(monitor, MonitorTestFixtures.NOW);
		monitor.applyTransition(
				new StateTransition(MonitorStatus.ONLINE, 0, 0, StateChangeReason.CHECK_SUCCEEDED),
				MonitorTestFixtures.NOW, oldExpiry);
		MonitorExecutionSnapshot snapshot = MonitorExecutionSnapshot.from(monitor);
		ExecutionResult result = ExecutionResult.success(2, oldExpiry.plusSeconds(30), null);

		MonitorRepository monitors = mock(MonitorRepository.class);
		MonitorCheckRepository checks = mock(MonitorCheckRepository.class);
		MonitorStateHistoryRepository history = mock(MonitorStateHistoryRepository.class);
		when(monitors.findByIdForUpdate(monitor.id())).thenReturn(Optional.of(monitor));
		when(checks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
		MonitorExecutionPersistence persistence = new MonitorExecutionPersistence(
				monitors, checks, history, new MonitorStateEngine(), freshness,
				mock(IncidentService.class), events);

		assertThat(persistence.complete(snapshot, result)).isPresent();

		ArgumentCaptor<MonitorStateHistory> transitions = ArgumentCaptor.forClass(MonitorStateHistory.class);
		verify(history, org.mockito.Mockito.times(2)).save(transitions.capture());
		assertThat(transitions.getAllValues())
				.extracting(MonitorStateHistory::fromStatus, MonitorStateHistory::toStatus,
						MonitorStateHistory::effectiveAt, MonitorStateHistory::reason)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(MonitorStatus.ONLINE, MonitorStatus.UNKNOWN,
								oldExpiry, StateChangeReason.OBSERVATION_STALE),
						org.assertj.core.groups.Tuple.tuple(MonitorStatus.UNKNOWN, MonitorStatus.ONLINE,
								result.checkedAt(), StateChangeReason.CHECK_SUCCEEDED));
		assertThat(monitor.lastCheckedAt()).isEqualTo(result.checkedAt());
		assertThat(monitor.observationValidUntil()).isEqualTo(freshness.validUntil(monitor, result.checkedAt()));
		ArgumentCaptor<MonitorChangedEvent> published = ArgumentCaptor.forClass(MonitorChangedEvent.class);
		verify(events).publishEvent(published.capture());
		assertThat(published.getValue().changes())
				.containsExactlyInAnyOrder(MonitorChange.CHECK_COMPLETED, MonitorChange.STATUS_CHANGED);
		assertThat(published.getValue().monitorId()).isEqualTo(monitor.id());
	}
}
