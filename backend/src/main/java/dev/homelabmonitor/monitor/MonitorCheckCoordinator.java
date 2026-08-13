package dev.homelabmonitor.monitor;

import java.time.Clock;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
class MonitorCheckCoordinator {

	private static final Logger log = LoggerFactory.getLogger(MonitorCheckCoordinator.class);
	private final MonitorExecutionPersistence persistence;
	private final Map<MonitorType, MonitorExecutor> executors;
	private final Executor taskExecutor;
	private final Executor manualTaskExecutor;
	private final Clock clock;
	private final Object stateLock = new Object();
	private final Set<UUID> queued = new HashSet<>();
	private final Set<UUID> inFlight = new HashSet<>();
	private final Semaphore manualSlots = new Semaphore(4);

	MonitorCheckCoordinator(
			MonitorExecutionPersistence persistence,
			List<MonitorExecutor> executors,
			@Qualifier("monitorTaskExecutor") Executor taskExecutor,
			@Qualifier("manualMonitorTaskExecutor") Executor manualTaskExecutor,
			Clock clock) {
		this.persistence = persistence;
		this.executors = new EnumMap<>(MonitorType.class);
		executors.forEach(executor -> this.executors.put(executor.type(), executor));
		this.taskExecutor = taskExecutor;
		this.manualTaskExecutor = manualTaskExecutor;
		this.clock = clock;
	}

	MonitorCheckResponse executeNow(UUID id) {
		if (!manualSlots.tryAcquire()) {
			throw new MonitorBusyException(id);
		}
		if (!claimManual(id)) {
			manualSlots.release();
			throw new MonitorBusyException(id);
		}
		MonitorExecutionSnapshot snapshot;
		try {
			snapshot = persistence.manualSnapshot(id);
		} catch (RuntimeException exception) {
			releaseRunning(id);
			manualSlots.release();
			throw exception;
		}
		FutureTask<Optional<MonitorCheckResponse>> task = new FutureTask<>(() -> {
			try {
				return executeAndPersist(snapshot);
			} finally {
				releaseRunning(id);
				manualSlots.release();
			}
		});
		try {
			manualTaskExecutor.execute(task);
			return task.get()
					.orElseThrow(() -> new InvalidMonitorException("Monitor changed while the check was running; result discarded."));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new InvalidMonitorException("Monitor check was interrupted.");
		} catch (java.util.concurrent.ExecutionException exception) {
			throw new InvalidMonitorException("Monitor check failed safely.");
		} catch (TaskRejectedException exception) {
			releaseRunning(id);
			manualSlots.release();
			throw new MonitorBusyException(id);
		}
	}

	boolean schedule(UUID id) {
		if (!enqueue(id)) {
			return false;
		}
		try {
			taskExecutor.execute(() -> executeScheduled(id));
			return true;
		} catch (TaskRejectedException exception) {
			removeQueued(id);
			log.warn("Monitor check queue is full; skipped monitor {}", id);
			return false;
		}
	}

	boolean claimForFreshness(UUID id) {
		synchronized (stateLock) {
			return inFlight.add(id);
		}
	}

	void releaseFreshnessClaim(UUID id) { releaseRunning(id); }

	private void executeScheduled(UUID id) {
		if (!startScheduled(id)) return;
		try {
			persistence.scheduledSnapshot(id).flatMap(this::executeAndPersist);
		} catch (RuntimeException exception) {
			log.error("Scheduled monitor check failed safely for monitor {}", id, exception);
		} finally {
			releaseRunning(id);
		}
	}

	private boolean claimManual(UUID id) {
		synchronized (stateLock) {
			return !queued.contains(id) && inFlight.add(id);
		}
	}

	private boolean enqueue(UUID id) {
		synchronized (stateLock) {
			return !inFlight.contains(id) && queued.add(id);
		}
	}

	private boolean startScheduled(UUID id) {
		synchronized (stateLock) {
			queued.remove(id);
			return inFlight.add(id);
		}
	}

	private void removeQueued(UUID id) {
		synchronized (stateLock) {
			queued.remove(id);
		}
	}

	private void releaseRunning(UUID id) {
		synchronized (stateLock) {
			inFlight.remove(id);
		}
	}

	private Optional<MonitorCheckResponse> executeAndPersist(MonitorExecutionSnapshot snapshot) {
		MonitorExecutor executor = executors.get(snapshot.type());
		ExecutionResult result;
		if (executor == null) {
			result = ExecutionResult.failure(
					CheckResultType.UNKNOWN_FAILURE, null, clock.instant(), "No executor is available for this monitor type.", null);
		} else {
			result = executor.execute(snapshot);
		}
		return persistence.complete(snapshot, result);
	}
}
