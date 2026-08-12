package dev.homelabmonitor.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record OwnerPrincipal(UUID id, String email, String displayName, String password) implements UserDetails {
	OwnerPrincipal withoutPassword() { return new OwnerPrincipal(id, email, displayName, null); }
	@Override public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_OWNER"));
	}
	@Override public String getPassword() { return password; }
	@Override public String getUsername() { return email; }
}
