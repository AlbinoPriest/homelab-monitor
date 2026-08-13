package dev.homelabmonitor.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MonitorCheckCoordinatorTests {

	@Test
	void rejectsManualCollisionForSameMonitor() throws Exception {
		UUID id = UUID.randomUUID();
		MonitorExecutionSnapshot snapshot = new MonitorExecutionSnapshot(
				id, 0, MonitorType.TCP, "localhost", 80, 500, null, null,
				MonitorStatus.UNKNOWN, 1, 1, 0, 0);
		MonitorCheckResponse response = new MonitorCheckResponse(
				UUID.randomUUID(), CheckResultType.SUCCESS, 1L, MonitorTestFixtures.NOW, null, null);
		MonitorExecutionPersistence persistence = mock(MonitorExecutionPersistence.class);
		when(persistence.manualSnapshot(id)).thenReturn(snapshot);

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		MonitorExecutor blockingExecutor = new MonitorExecutor() {
			@Override
			public MonitorType type() {
				return MonitorType.TCP;
			}

			@Override
			public ExecutionResult execute(MonitorExecutionSnapshot ignored) {
				started.countDown();
				try {
					release.await(2, TimeUnit.SECONDS);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				return ExecutionResult.success(1, MonitorTestFixtures.NOW, null);
			}
		};
		when(persistence.complete(snapshot, ExecutionResult.success(1, MonitorTestFixtures.NOW, null)))
				.thenReturn(Optional.of(response));

		MonitorCheckCoordinator coordinator = coordinator(persistence, List.of(blockingExecutor), Runnable::run);
		try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
			Future<MonitorCheckResponse> first = caller.submit(() -> coordinator.executeNow(id));
			assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> coordinator.executeNow(id)).isInstanceOf(MonitorBusyException.class);
			release.countDown();
			assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo(response);
		}
	}

	@Test
	void claimsScheduledMonitorBeforeQueuingWork() {
		UUID id = UUID.randomUUID();
		MonitorExecutionPersistence persistence = mock(MonitorExecutionPersistence.class);
		when(persistence.scheduledSnapshot(id)).thenReturn(Optional.empty());
		CapturingExecutor executor = new CapturingExecutor();
		MonitorCheckCoordinator coordinator = coordinator(persistence, List.of(), executor);

		assertThat(coordinator.schedule(id)).isTrue();
		assertThat(coordinator.schedule(id)).isFalse();
		assertThatThrownBy(() -> coordinator.executeNow(id)).isInstanceOf(MonitorBusyException.class);
		executor.runCaptured();
		assertThat(coordinator.schedule(id)).isTrue();
	}

	@Test
	void queuedWorkDoesNotSuppressFreshnessAndDefersWhenScannerOwnsTheRunningGate() {
		UUID id = UUID.randomUUID();
		MonitorExecutionPersistence persistence = mock(MonitorExecutionPersistence.class);
		CapturingExecutor executor = new CapturingExecutor();
		MonitorCheckCoordinator coordinator = coordinator(persistence, List.of(), executor);

		assertThat(coordinator.schedule(id)).isTrue();
		assertThat(coordinator.claimForFreshness(id)).isTrue();
		executor.runCaptured();
		org.mockito.Mockito.verify(persistence, org.mockito.Mockito.never()).scheduledSnapshot(id);
		coordinator.releaseFreshnessClaim(id);
		assertThat(coordinator.schedule(id)).isTrue();
	}

	@Test
	void keepsManualClaimUntilWorkerEndsAfterCallerIsInterrupted() throws Exception {
		UUID id = UUID.randomUUID();
		MonitorExecutionSnapshot snapshot = new MonitorExecutionSnapshot(
				id, 0, MonitorType.TCP, "localhost", 80, 500, null, null,
				MonitorStatus.UNKNOWN, 1, 1, 0, 0);
		MonitorExecutionPersistence persistence = mock(MonitorExecutionPersistence.class);
		when(persistence.manualSnapshot(id)).thenReturn(snapshot);
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		MonitorExecutor blockingExecutor = new MonitorExecutor() {
			@Override
			public MonitorType type() {
				return MonitorType.TCP;
			}

			@Override
			public ExecutionResult execute(MonitorExecutionSnapshot ignored) {
				started.countDown();
				while (release.getCount() > 0) {
					try {
						release.await();
					} catch (InterruptedException ignoredException) {
						// Simulate network work that does not stop promptly when its caller disconnects.
					}
				}
				return ExecutionResult.success(1, MonitorTestFixtures.NOW, null);
			}
		};

		try (ExecutorService workers = Executors.newSingleThreadExecutor();
				ExecutorService caller = Executors.newSingleThreadExecutor()) {
			MonitorCheckCoordinator coordinator = new MonitorCheckCoordinator(
					persistence, List.of(blockingExecutor), Runnable::run, workers,
					Clock.fixed(MonitorTestFixtures.NOW, ZoneOffset.UTC));
			Future<MonitorCheckResponse> request = caller.submit(() -> coordinator.executeNow(id));
			assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
			assertThat(request.cancel(true)).isTrue();
			assertThatThrownBy(() -> coordinator.executeNow(id)).isInstanceOf(MonitorBusyException.class);
			release.countDown();
			workers.submit(() -> {}).get(1, TimeUnit.SECONDS);
			when(persistence.manualSnapshot(id)).thenThrow(new MonitorNotFoundException(id));
			assertThatThrownBy(() -> coordinator.executeNow(id)).isInstanceOf(MonitorNotFoundException.class);
		}
	}

	private MonitorCheckCoordinator coordinator(
			MonitorExecutionPersistence persistence, List<MonitorExecutor> executors, Executor executor) {
		return new MonitorCheckCoordinator(
				persistence, executors, executor, executor, Clock.fixed(MonitorTestFixtures.NOW, ZoneOffset.UTC));
	}

	private static final class CapturingExecutor implements Executor {
		private Runnable command;

		@Override
		public void execute(Runnable command) {
			this.command = command;
		}

		void runCaptured() {
			command.run();
		}
	}
}
