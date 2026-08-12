package dev.homelabmonitor.auth;

public class InvalidCredentialsException extends RuntimeException {
	InvalidCredentialsException() { super("Email or password is incorrect."); }
}
