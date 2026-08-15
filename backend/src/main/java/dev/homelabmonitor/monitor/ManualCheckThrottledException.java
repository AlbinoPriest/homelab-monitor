package dev.homelabmonitor.monitor;

public class ManualCheckThrottledException extends RuntimeException {
	public ManualCheckThrottledException() {
		super("Too many manual checks. Try again in one second.");
	}
}
