package dev.homelabmonitor.incident;

public enum IncidentOutageReason {
	TIMEOUT,
	DNS_FAILURE,
	CONNECTION_REFUSED,
	TLS_ERROR,
	UNEXPECTED_STATUS,
	INVALID_TARGET,
	UNKNOWN_FAILURE
}
