package dev.homelabmonitor.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
	private final OwnerService ownerService;
	private final AuthenticationManager authenticationManager;
	private final LoginAttemptLimiter loginAttemptLimiter;
	private final ClientAddressResolver clientAddressResolver;
	private final ApplicationEventPublisher eventPublisher;
	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	AuthController(OwnerService ownerService, AuthenticationManager authenticationManager,
			LoginAttemptLimiter loginAttemptLimiter, ClientAddressResolver clientAddressResolver,
			ApplicationEventPublisher eventPublisher) {
		this.ownerService = ownerService;
		this.authenticationManager = authenticationManager;
		this.loginAttemptLimiter = loginAttemptLimiter;
		this.clientAddressResolver = clientAddressResolver;
		this.eventPublisher = eventPublisher;
	}

	@GetMapping("/status")
	AuthResponse status(Authentication authentication) {
		OwnerPrincipal principal = principal(authentication);
		return new AuthResponse(ownerService.setupRequired(), principal != null,
				principal == null ? null : AuthResponse.OwnerView.from(principal));
	}

	@PostMapping("/setup")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResponse setup(@Valid @RequestBody AuthRequests.Setup request,
			HttpServletRequest servletRequest, HttpServletResponse response) {
		OwnerPrincipal principal = ownerService.setup(request);
		signIn(principal, request.password(), servletRequest, response);
		return new AuthResponse(false, true, AuthResponse.OwnerView.from(principal));
	}

	@PostMapping("/login")
	AuthResponse login(@Valid @RequestBody AuthRequests.Login request,
			HttpServletRequest servletRequest, HttpServletResponse response) {
		ownerService.validateLoginPassword(request.password());
		loginAttemptLimiter.acquire(clientAddressResolver.resolve(servletRequest), request.email());
		try {
			Authentication authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
			persist(authentication, servletRequest, response);
			OwnerPrincipal principal = (OwnerPrincipal) authentication.getPrincipal();
			return new AuthResponse(false, true, AuthResponse.OwnerView.from(principal));
		} catch (BadCredentialsException exception) {
			throw new InvalidCredentialsException();
		}
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		if (request.getSession(false) != null) {
			eventPublisher.publishEvent(new OwnerSessionEndedEvent(request.getSession(false).getId()));
			request.getSession(false).invalidate();
		}
	}

	private void signIn(OwnerPrincipal principal, String password,
			HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(principal.email(), password));
		persist(authentication, request, response);
	}

	private void persist(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) request.changeSessionId();
		OwnerPrincipal owner = ((OwnerPrincipal) authentication.getPrincipal()).withoutPassword();
		Authentication sessionAuthentication = UsernamePasswordAuthenticationToken.authenticated(
				owner, null, authentication.getAuthorities());
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(sessionAuthentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

	private OwnerPrincipal principal(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof OwnerPrincipal owner)) return null;
		return owner;
	}
}
