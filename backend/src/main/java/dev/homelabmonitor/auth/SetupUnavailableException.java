package dev.homelabmonitor.auth;

public class SetupUnavailableException extends RuntimeException {
	SetupUnavailableException() { super("Owner setup has already been completed."); }
}
