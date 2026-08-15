package dev.homelabmonitor.realtime;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class RealtimeBroker {
	private static final int MAX_PENDING_EVENTS_PER_CONNECTION = 32;

	private final Map<UUID, Subscription> subscriptions = new LinkedHashMap<>();
	private final int maxConnections;
	private final long connectionTimeoutMillis;
	private final Clock clock;
	private final LongFunction<SseEmitter> emitterFactory;
	private final Executor deliveryExecutor;

	@Autowired
	RealtimeBroker(
			@Value("${homelab-monitor.realtime.max-connections:8}") int maxConnections,
			@Value("${homelab-monitor.realtime.connection-timeout:300000}") long connectionTimeoutMillis,
			Clock clock,
			@Qualifier("realtimeDeliveryExecutor") Executor deliveryExecutor) {
		this(maxConnections, connectionTimeoutMillis, clock, SseEmitter::new, deliveryExecutor);
	}

	RealtimeBroker(int maxConnections, long connectionTimeoutMillis, Clock clock,
			LongFunction<SseEmitter> emitterFactory, Executor deliveryExecutor) {
		if (maxConnections < 1 || maxConnections > 100) {
			throw new IllegalArgumentException("Realtime connections must be between 1 and 100.");
		}
		if (connectionTimeoutMillis < 30_000) {
			throw new IllegalArgumentException("Realtime connection timeout must be at least 30 seconds.");
		}
		this.maxConnections = maxConnections;
		this.connectionTimeoutMillis = connectionTimeoutMillis;
		this.clock = clock;
		this.emitterFactory = emitterFactory;
		this.deliveryExecutor = deliveryExecutor;
	}

	SseEmitter subscribe(String sessionId) {
		UUID subscriptionId = UUID.randomUUID();
		SseEmitter emitter = emitterFactory.apply(connectionTimeoutMillis);
		Subscription subscription = new Subscription(sessionId, emitter);
		synchronized (this) {
			if (subscriptions.size() >= maxConnections) {
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
						"Realtime connection capacity is temporarily exhausted.");
			}
			subscriptions.put(subscriptionId, subscription);
		}
		emitter.onCompletion(() -> remove(subscriptionId));
		emitter.onTimeout(() -> remove(subscriptionId));
		emitter.onError(error -> remove(subscriptionId));
		QueueResult ready = subscription.enqueue(() -> SseEmitter.event()
				.name("ready")
				.reconnectTime(3_000)
				.data(Map.of("connectedAt", clock.instant())));
		if (ready == QueueResult.START_DRAIN) scheduleDrain(subscriptionId, subscription);
		return emitter;
	}

	void publish(RealtimeEvent event) {
		dispatch(() -> SseEmitter.event().data(event));
	}

	@Scheduled(fixedDelayString = "${homelab-monitor.realtime.heartbeat-delay:15000}")
	void heartbeat() {
		dispatch(() -> SseEmitter.event().comment("heartbeat"));
	}

	void closeSession(String sessionId) {
		List<Map.Entry<UUID, Subscription>> closing;
		synchronized (this) {
			closing = subscriptions.entrySet().stream()
					.filter(entry -> entry.getValue().sessionId().equals(sessionId))
					.map(entry -> Map.entry(entry.getKey(), entry.getValue()))
					.toList();
		}
		closing.forEach(entry -> {
			if (entry.getValue().terminateWithoutWaiting()) remove(entry.getKey(), entry.getValue());
		});
	}

	@PreDestroy
	void close() {
		List<Subscription> closing;
		synchronized (this) {
			closing = List.copyOf(subscriptions.values());
			subscriptions.clear();
		}
		closing.forEach(Subscription::terminateWithoutWaiting);
	}

	synchronized int connectionCount() {
		return subscriptions.size();
	}

	private void dispatch(Supplier<SseEmitter.SseEventBuilder> event) {
		List<Map.Entry<UUID, Subscription>> current;
		synchronized (this) {
			current = new ArrayList<>(subscriptions.entrySet());
		}
		for (Map.Entry<UUID, Subscription> entry : current) {
			QueueResult result = entry.getValue().enqueue(event);
			if (result == QueueResult.FULL) {
				evict(entry.getKey(), entry.getValue(), new IOException("Realtime client is not consuming events."));
			} else if (result == QueueResult.START_DRAIN) {
				scheduleDrain(entry.getKey(), entry.getValue());
			}
		}
	}

	private void scheduleDrain(UUID subscriptionId, Subscription subscription) {
		try {
			deliveryExecutor.execute(() -> drain(subscriptionId, subscription));
		} catch (RuntimeException exception) {
			evict(subscriptionId, subscription, exception);
		}
	}

	private void drain(UUID subscriptionId, Subscription subscription) {
		try {
			while (subscription.sendNext()) {
				// Drain this connection's bounded lane serially.
			}
		} catch (IOException | RuntimeException exception) {
			evict(subscriptionId, subscription, exception);
		} finally {
			if (subscription.isClosed()) {
				subscription.finishTermination();
				remove(subscriptionId, subscription);
			}
		}
	}

	private void evict(UUID subscriptionId, Subscription subscription, Throwable exception) {
		if (!contains(subscriptionId, subscription)) return;
		if (subscription.terminateWithErrorWithoutWaiting(exception)) remove(subscriptionId, subscription);
	}

	private void remove(UUID subscriptionId) {
		Subscription subscription;
		synchronized (this) {
			subscription = subscriptions.get(subscriptionId);
		}
		if (subscription != null && subscription.markClosedWithoutWaiting()) {
			remove(subscriptionId, subscription);
		}
	}

	private synchronized boolean contains(UUID subscriptionId, Subscription subscription) {
		return subscriptions.get(subscriptionId) == subscription;
	}

	private synchronized boolean remove(UUID subscriptionId, Subscription subscription) {
		return subscriptions.remove(subscriptionId, subscription);
	}

	private enum QueueResult { QUEUED, START_DRAIN, FULL, CLOSED }

	private static final class Subscription {
		private final String sessionId;
		private final SseEmitter emitter;
		private final ArrayDeque<Supplier<SseEmitter.SseEventBuilder>> pending = new ArrayDeque<>();
		private final ReentrantLock deliveryLock = new ReentrantLock();
		private boolean draining;
		private volatile boolean closed;
		private volatile Thread deliveryThread;
		private boolean completionRequested;
		private boolean emitterCompleted;
		private Throwable terminalError;

		private Subscription(String sessionId, SseEmitter emitter) {
			this.sessionId = sessionId;
			this.emitter = emitter;
		}

		synchronized QueueResult enqueue(Supplier<SseEmitter.SseEventBuilder> event) {
			if (closed) return QueueResult.CLOSED;
			if (pending.size() >= MAX_PENDING_EVENTS_PER_CONNECTION) return QueueResult.FULL;
			pending.addLast(event);
			if (draining) return QueueResult.QUEUED;
			draining = true;
			return QueueResult.START_DRAIN;
		}

		boolean sendNext() throws IOException {
			Supplier<SseEmitter.SseEventBuilder> event;
			synchronized (this) {
				if (closed) return false;
				event = pending.pollFirst();
				if (event == null) {
					draining = false;
					return false;
				}
			}
			deliveryLock.lock();
			try {
				deliveryThread = Thread.currentThread();
				if (closed) return false;
				emitter.send(event.get());
				return !closed;
			} finally {
				deliveryThread = null;
				deliveryLock.unlock();
			}
		}

		boolean terminateWithoutWaiting() {
			requestTermination(null);
			return completeIfQuiescent();
		}

		boolean terminateWithErrorWithoutWaiting(Throwable exception) {
			requestTermination(exception);
			return completeIfQuiescent();
		}

		boolean markClosedWithoutWaiting() {
			interruptDelivery();
			return isQuiescent();
		}

		private void interruptDelivery() {
			Thread writer = closeState();
			if (writer != null) writer.interrupt();
		}

		private void requestTermination(Throwable exception) {
			Thread writer;
			synchronized (this) {
				if (!completionRequested) terminalError = exception;
				completionRequested = true;
				writer = closeState();
			}
			if (writer != null) writer.interrupt();
		}

		private synchronized Thread closeState() {
			closed = true;
			pending.clear();
			draining = false;
			return deliveryThread;
		}

		private boolean isQuiescent() {
			if (!deliveryLock.tryLock()) return false;
			try {
				return deliveryThread == null;
			} finally {
				deliveryLock.unlock();
			}
		}

		private boolean completeIfQuiescent() {
			if (!deliveryLock.tryLock()) return false;
			try {
				completeEmitter();
				return deliveryThread == null;
			} finally {
				deliveryLock.unlock();
			}
		}

		void finishTermination() {
			deliveryLock.lock();
			try {
				completeEmitter();
			} finally {
				deliveryLock.unlock();
			}
		}

		private void completeEmitter() {
			Throwable error;
			synchronized (this) {
				if (!completionRequested || emitterCompleted) return;
				emitterCompleted = true;
				error = terminalError;
			}
			if (error == null) emitter.complete();
			else emitter.completeWithError(error);
		}

		boolean isClosed() { return closed; }
		String sessionId() { return sessionId; }
		SseEmitter emitter() { return emitter; }
	}
}
