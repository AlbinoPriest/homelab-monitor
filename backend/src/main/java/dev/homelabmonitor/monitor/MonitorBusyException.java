package dev.homelabmonitor.monitor;

import java.util.UUID;

public class MonitorBusyException extends RuntimeException {

	public MonitorBusyException(UUID id) {
		super("A check is already running for monitor " + id);
	}
}
