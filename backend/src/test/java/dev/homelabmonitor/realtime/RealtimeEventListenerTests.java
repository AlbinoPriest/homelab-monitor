package dev.homelabmonitor.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.homelabmonitor.auth.OwnerSessionEndedEvent;
import dev.homelabmonitor.monitor.MonitorChange;
import dev.homelabmonitor.monitor.MonitorChangedEvent;
import dev.homelabmonitor.monitor.MonitorStatus;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class RealtimeEventListenerTests {
	@Test
	void forwardsACompactCurrentChangePayload() {
		RealtimeBroker broker = mock(RealtimeBroker.class);
		RealtimeEventListener listener = new RealtimeEventListener(broker);
		UUID monitorId = UUID.randomUUID();
		UUID checkId = UUID.randomUUID();
		Instant occurredAt = Instant.parse("2030-01-01T00:00:00Z");

		listener.monitorChanged(new MonitorChangedEvent(monitorId, occurredAt,
				EnumSet.of(MonitorChange.STATUS_CHANGED, MonitorChange.CHECK_COMPLETED),
				MonitorStatus.OFFLINE, checkId));

		ArgumentCaptor<RealtimeEvent> event = ArgumentCaptor.forClass(RealtimeEvent.class);
		verify(broker).publish(event.capture());
		assertThat(event.getValue()).isEqualTo(new RealtimeEvent(monitorId, occurredAt,
				List.of(MonitorChange.CHECK_COMPLETED, MonitorChange.STATUS_CHANGED),
				MonitorStatus.OFFLINE, checkId));
	}

	@Test
	void listensOnlyAfterTheSurroundingTransactionCommits() throws Exception {
		TransactionalEventListener annotation = RealtimeEventListener.class
				.getDeclaredMethod("monitorChanged", MonitorChangedEvent.class)
				.getAnnotation(TransactionalEventListener.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
		assertThat(annotation.fallbackExecution()).isFalse();
	}

	@Test
	void closesStreamsForExplicitLogoutAndContainerSessionExpiry() {
		RealtimeBroker broker = mock(RealtimeBroker.class);
		RealtimeEventListener events = new RealtimeEventListener(broker);
		events.ownerSessionEnded(new OwnerSessionEndedEvent("logout-session"));

		HttpSession expired = mock(HttpSession.class);
		org.mockito.Mockito.when(expired.getId()).thenReturn("expired-session");
		new RealtimeSessionListener(broker).sessionDestroyed(new HttpSessionEvent(expired));

		verify(broker).closeSession("logout-session");
		verify(broker).closeSession("expired-session");
	}
}
