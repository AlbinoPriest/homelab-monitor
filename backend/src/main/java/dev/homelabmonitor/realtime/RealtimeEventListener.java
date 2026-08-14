package dev.homelabmonitor.realtime;

import dev.homelabmonitor.auth.OwnerSessionEndedEvent;
import dev.homelabmonitor.monitor.MonitorChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class RealtimeEventListener {
	private final RealtimeBroker broker;

	RealtimeEventListener(RealtimeBroker broker) {
		this.broker = broker;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void monitorChanged(MonitorChangedEvent event) {
		broker.publish(RealtimeEvent.from(event));
	}

	@EventListener
	void ownerSessionEnded(OwnerSessionEndedEvent event) {
		broker.closeSession(event.sessionId());
	}
}
