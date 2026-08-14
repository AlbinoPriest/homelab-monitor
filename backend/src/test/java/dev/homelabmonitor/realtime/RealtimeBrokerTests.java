package dev.homelabmonitor.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RealtimeBrokerTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void boundsConnectionsAndRemovesCompletedStreams() throws Exception {
		SseEmitter emitter = mock(SseEmitter.class);
		RealtimeBroker broker = new RealtimeBroker(1, 30_000, CLOCK, ignored -> emitter, Runnable::run);

		broker.subscribe("session-one");
		assertThat(broker.connectionCount()).isEqualTo(1);
		assertThatThrownBy(() -> broker.subscribe("session-two"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("503 SERVICE_UNAVAILABLE");

		ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
		verify(emitter).onCompletion(completion.capture());
		completion.getValue().run();
		assertThat(broker.connectionCount()).isZero();
	}

	@Test
	void removesAStreamWhenDeliveryFailsWithoutAffectingPublishers() throws Exception {
		SseEmitter emitter = mock(SseEmitter.class);
		doNothing().doThrow(new IOException("disconnected"))
				.when(emitter).send(any(SseEmitter.SseEventBuilder.class));
		RealtimeBroker broker = new RealtimeBroker(2, 30_000, CLOCK, ignored -> emitter, Runnable::run);
		broker.subscribe("session-one");

		broker.heartbeat();

		assertThat(broker.connectionCount()).isZero();
		verify(emitter).completeWithError(any(IOException.class));
	}

	@Test
	void timeoutAndErrorCallbacksReleaseConnectionCapacity() throws Exception {
		SseEmitter timedOut = mock(SseEmitter.class);
		RealtimeBroker timeoutBroker = new RealtimeBroker(
				1, 30_000, CLOCK, ignored -> timedOut, Runnable::run);
		timeoutBroker.subscribe("timed-out-session");
		ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
		verify(timedOut).onTimeout(timeout.capture());
		timeout.getValue().run();
		assertThat(timeoutBroker.connectionCount()).isZero();

		SseEmitter failed = mock(SseEmitter.class);
		RealtimeBroker errorBroker = new RealtimeBroker(1, 30_000, CLOCK, ignored -> failed, Runnable::run);
		errorBroker.subscribe("failed-session");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Consumer<Throwable>> error = ArgumentCaptor.forClass(Consumer.class);
		verify(failed).onError(error.capture());
		error.getValue().accept(new IOException("client closed"));
		assertThat(errorBroker.connectionCount()).isZero();
	}

	@Test
	void aBlockedClientDoesNotBlockPublishersOrAnotherClient() throws Exception {
		SseEmitter blocked = mock(SseEmitter.class);
		SseEmitter healthy = mock(SseEmitter.class);
		CountDownLatch blockedSendStarted = new CountDownLatch(1);
		CountDownLatch releaseBlockedSend = new CountDownLatch(1);
		doNothing().doAnswer(invocation -> {
			blockedSendStarted.countDown();
			releaseBlockedSend.await(2, TimeUnit.SECONDS);
			return null;
		}).when(blocked).send(any(SseEmitter.SseEventBuilder.class));
		java.util.concurrent.atomic.AtomicInteger created = new java.util.concurrent.atomic.AtomicInteger();
		try (ExecutorService delivery = Executors.newFixedThreadPool(2)) {
			RealtimeBroker broker = new RealtimeBroker(2, 30_000, CLOCK,
					ignored -> created.getAndIncrement() == 0 ? blocked : healthy, delivery);
			broker.subscribe("slow-session");
			broker.subscribe("healthy-session");

			broker.publish(new RealtimeEvent(UUID.randomUUID(), CLOCK.instant(), List.of(), null, null));

			assertThat(blockedSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
			verify(healthy, org.mockito.Mockito.timeout(1_000).times(2))
					.send(any(SseEmitter.SseEventBuilder.class));
			releaseBlockedSend.countDown();
		}
	}

	@Test
	void sessionClosureWaitsForAnInflightSendAndPreventsLaterWrites() throws Exception {
		SseEmitter emitter = mock(SseEmitter.class);
		CountDownLatch sendStarted = new CountDownLatch(1);
		CountDownLatch releaseSend = new CountDownLatch(1);
		CountDownLatch sendFinished = new CountDownLatch(1);
		doNothing().doAnswer(invocation -> {
			sendStarted.countDown();
			releaseSend.await(2, TimeUnit.SECONDS);
			sendFinished.countDown();
			return null;
		}).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
		try (ExecutorService delivery = Executors.newSingleThreadExecutor();
				ExecutorService closer = Executors.newSingleThreadExecutor()) {
			RealtimeBroker broker = new RealtimeBroker(1, 30_000, CLOCK, ignored -> emitter, delivery);
			broker.subscribe("closing-session");
			broker.publish(new RealtimeEvent(UUID.randomUUID(), CLOCK.instant(), List.of(), null, null));
			assertThat(sendStarted.await(1, TimeUnit.SECONDS)).isTrue();

			Future<?> closing = closer.submit(() -> broker.closeSession("closing-session"));
			verify(emitter, org.mockito.Mockito.timeout(1_000)).complete();
			assertThat(closing.isDone()).isFalse();
			releaseSend.countDown();
			closing.get(1, TimeUnit.SECONDS);
			assertThat(sendFinished.getCount()).isZero();

			broker.publish(new RealtimeEvent(UUID.randomUUID(), CLOCK.instant(), List.of(), null, null));
			verify(emitter, org.mockito.Mockito.after(100).times(2))
					.send(any(SseEmitter.SseEventBuilder.class));
		}
	}
}
