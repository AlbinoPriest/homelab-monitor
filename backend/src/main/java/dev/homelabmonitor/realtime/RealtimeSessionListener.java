package dev.homelabmonitor.realtime;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

class RealtimeSessionListener implements HttpSessionListener {
	private final RealtimeBroker broker;

	RealtimeSessionListener(RealtimeBroker broker) {
		this.broker = broker;
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		broker.closeSession(event.getSession().getId());
	}
}
