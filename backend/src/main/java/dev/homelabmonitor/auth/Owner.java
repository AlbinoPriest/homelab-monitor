package dev.homelabmonitor.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "owners")
class Owner {
	@Id private UUID id;
	@Column(name = "singleton_key", nullable = false) private short singletonKey;
	@Column(nullable = false, length = 254) private String email;
	@Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
	@Column(name = "display_name", nullable = false, length = 120) private String displayName;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;

	protected Owner() {}

	Owner(String email, String passwordHash, String displayName, Instant now) {
		this.id = UUID.randomUUID();
		this.singletonKey = 1;
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.createdAt = now;
		this.updatedAt = now;
	}

	UUID id() { return id; }
	String email() { return email; }
	String passwordHash() { return passwordHash; }
	String displayName() { return displayName; }
}
