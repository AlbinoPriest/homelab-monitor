package dev.homelabmonitor.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerService {
	private final OwnerRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	OwnerService(OwnerRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	boolean setupRequired() { return repository.count() == 0; }

	public OwnerPrincipal load(String email) {
		Owner owner = repository.findByEmail(normalize(email))
				.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
		return principal(owner);
	}

	void validateLoginPassword(String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new InvalidCredentialsException();
	}

	@Transactional
	OwnerPrincipal setup(AuthRequests.Setup request) {
		if (repository.count() != 0) throw new SetupUnavailableException();
		String password = request.password();
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new InvalidAuthRequestException("Password must be at most 72 UTF-8 bytes.");
		}
		Owner owner = new Owner(normalize(request.email()), passwordEncoder.encode(password),
				request.displayName().trim(), clock.instant());
		try {
			return principal(repository.saveAndFlush(owner));
		} catch (DataIntegrityViolationException exception) {
			throw new SetupUnavailableException();
		}
	}

	private OwnerPrincipal principal(Owner owner) {
		return new OwnerPrincipal(owner.id(), owner.email(), owner.displayName(), owner.passwordHash());
	}

	private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
