package dev.homelabmonitor.monitor;

public enum CheckResultType {
	SUCCESS,
	TIMEOUT,
	DNS_FAILURE,
	CONNECTION_REFUSED,
	TLS_ERROR,
	UNEXPECTED_STATUS,
	INVALID_TARGET,
	UNKNOWN_FAILURE
}
