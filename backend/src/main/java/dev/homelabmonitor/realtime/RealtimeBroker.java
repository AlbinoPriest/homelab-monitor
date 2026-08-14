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

	synchronized SseEmitter subscribe(String sessionId) {
		if (subscriptions.size() >= maxConnections) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Realtime connection capacity is temporarily exhausted.");
		}
		UUID subscriptionId = UUID.randomUUID();
		SseEmitter emitter = emitterFactory.apply(connectionTimeoutMillis);
		Subscription subscription = new Subscription(sessionId, emitter);
		subscriptions.put(subscriptionId, subscription);
		emitter.onCompletion(() -> remove(subscriptionId));
		emitter.onTimeout(() -> remove(subscriptionId));
		emitter.onError(error -> remove(subscriptionId));
		try {
			emitter.send(SseEmitter.event()
					.name("ready")
					.reconnectTime(3_000)
					.data(Map.of("connectedAt", clock.instant())));
		} catch (IOException | RuntimeException exception) {
			subscriptions.remove(subscriptionId);
			emitter.completeWithError(exception);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Realtime connection could not be established.", exception);
		}
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
		List<Subscription> closing = new ArrayList<>();
		synchronized (this) {
			subscriptions.entrySet().removeIf(entry -> {
				if (!entry.getValue().sessionId().equals(sessionId)) return false;
				closing.add(entry.getValue());
				return true;
			});
		}
		closing.forEach(subscription -> {
			subscription.terminate();
		});
	}

	@PreDestroy
	void close() {
		List<Subscription> closing;
		synchronized (this) {
			closing = List.copyOf(subscriptions.values());
			subscriptions.clear();
		}
		closing.forEach(subscription -> {
			subscription.terminate();
		});
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
				try {
					deliveryExecutor.execute(() -> drain(entry.getKey(), entry.getValue()));
				} catch (RuntimeException exception) {
					evict(entry.getKey(), entry.getValue(), exception);
				}
			}
		}
	}

	private void drain(UUID subscriptionId, Subscription subscription) {
		try {
			while (subscription.sendNext()) {
				// Drain this connection's bounded lane serially.
			}
		} catch (IOException | RuntimeException exception) {
			evict(subscriptionId, subscription, exception);
		}
	}

	private void evict(UUID subscriptionId, Subscription subscription, Throwable exception) {
		if (!remove(subscriptionId, subscription)) return;
		subscription.terminateWithError(exception);
	}

	private synchronized void remove(UUID subscriptionId) {
		Subscription removed = subscriptions.remove(subscriptionId);
		if (removed != null) removed.markClosed();
	}

	private synchronized boolean remove(UUID subscriptionId, Subscription subscription) {
		return subscriptions.remove(subscriptionId, subscription);
	}

	private enum QueueResult { QUEUED, START_DRAIN, FULL, CLOSED }

	private static final class Subscription {
		private final String sessionId;
		private final SseEmitter emitter;
		private final ArrayDeque<Supplier<SseEmitter.SseEventBuilder>> pending = new ArrayDeque<>();
		private boolean draining;
		private volatile boolean closed;

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

		synchronized boolean sendNext() throws IOException {
			if (closed) return false;
			Supplier<SseEmitter.SseEventBuilder> event = pending.pollFirst();
			if (event == null) {
				draining = false;
				return false;
			}
			emitter.send(event.get());
			return true;
		}

		void terminate() {
			closed = true;
			emitter.complete();
			awaitQuiescence();
		}

		void terminateWithError(Throwable exception) {
			closed = true;
			emitter.completeWithError(exception);
			awaitQuiescence();
		}

		void markClosed() {
			closed = true;
			awaitQuiescence();
		}

		private synchronized void awaitQuiescence() {
			pending.clear();
			draining = false;
		}

		String sessionId() { return sessionId; }
		SseEmitter emitter() { return emitter; }
	}
}
