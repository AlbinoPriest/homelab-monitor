package dev.homelabmonitor.auth;

public class InvalidAuthRequestException extends RuntimeException {
	InvalidAuthRequestException(String message) { super(message); }
}
