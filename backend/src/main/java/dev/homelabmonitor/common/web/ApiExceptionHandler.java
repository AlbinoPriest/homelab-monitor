package dev.homelabmonitor.common.web;

import dev.homelabmonitor.analytics.InvalidAnalyticsRequestException;
import dev.homelabmonitor.auth.InvalidAuthRequestException;
import dev.homelabmonitor.auth.InvalidCredentialsException;
import dev.homelabmonitor.auth.LoginThrottledException;
import dev.homelabmonitor.auth.SetupUnavailableException;
import dev.homelabmonitor.monitor.InvalidMonitorException;
import dev.homelabmonitor.monitor.ManualCheckThrottledException;
import dev.homelabmonitor.monitor.MonitorBusyException;
import dev.homelabmonitor.monitor.MonitorNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(LoginThrottledException.class)
	ResponseEntity<ProblemDetail> loginThrottled(LoginThrottledException exception) {
		ProblemDetail detail = problem(HttpStatus.TOO_MANY_REQUESTS, "Authentication temporarily limited",
				exception.getMessage());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", Long.toString(exception.retryAfter().toSeconds()))
				.body(detail);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ProblemDetail invalidCredentials(InvalidCredentialsException exception) {
		return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", exception.getMessage());
	}

	@ExceptionHandler(SetupUnavailableException.class)
	ProblemDetail setupUnavailable(SetupUnavailableException exception) {
		return problem(HttpStatus.CONFLICT, "Setup unavailable", exception.getMessage());
	}

	@ExceptionHandler(InvalidAuthRequestException.class)
	ProblemDetail invalidAuthRequest(InvalidAuthRequestException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid authentication request", exception.getMessage());
	}

	@ExceptionHandler(MonitorNotFoundException.class)
	ProblemDetail notFound(MonitorNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Monitor not found", exception.getMessage());
	}

	@ExceptionHandler(MonitorBusyException.class)
	ProblemDetail conflict(MonitorBusyException exception) {
		return problem(HttpStatus.CONFLICT, "Monitor check already running", exception.getMessage());
	}

	@ExceptionHandler(ManualCheckThrottledException.class)
	ResponseEntity<ProblemDetail> manualCheckThrottled(ManualCheckThrottledException exception) {
		ProblemDetail detail = problem(HttpStatus.TOO_MANY_REQUESTS, "Manual checks temporarily limited",
				exception.getMessage());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "1").body(detail);
	}

	@ExceptionHandler(InvalidMonitorException.class)
	ProblemDetail invalidMonitor(InvalidMonitorException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid monitor", exception.getMessage());
	}

	@ExceptionHandler(InvalidAnalyticsRequestException.class)
	ProblemDetail invalidAnalyticsRequest(InvalidAnalyticsRequestException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid analytics request", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail validation(MethodArgumentNotValidException exception) {
		ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.");
		List<String> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.toList();
		detail.setProperty("errors", errors);
		return detail;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail constraintViolation(ConstraintViolationException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more request parameters are invalid.");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("about:blank"));
		return problem;
	}
}
