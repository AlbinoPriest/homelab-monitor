package dev.homelabmonitor.monitor;

import java.util.UUID;

public class MonitorNotFoundException extends RuntimeException {

	public MonitorNotFoundException(UUID id) {
		super("Monitor not found: " + id);
	}
}
