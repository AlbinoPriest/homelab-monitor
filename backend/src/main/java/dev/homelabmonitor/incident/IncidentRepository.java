package dev.homelabmonitor.incident;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface IncidentRepository extends JpaRepository<Incident, UUID> {
	Optional<Incident> findByMonitorIdAndStatus(UUID monitorId, IncidentStatus status);
	Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);
	Page<Incident> findByMonitorId(UUID monitorId, Pageable pageable);
	Page<Incident> findByMonitorIdAndStatus(UUID monitorId, IncidentStatus status, Pageable pageable);
}
