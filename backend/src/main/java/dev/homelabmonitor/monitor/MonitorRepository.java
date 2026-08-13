package dev.homelabmonitor.monitor;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MonitorRepository extends JpaRepository<Monitor, UUID> {

	List<Monitor> findByEnabledTrueAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(
			Instant now, Pageable pageable);

	List<Monitor> findByEnabledTrueAndStatusInAndObservationValidUntilLessThanEqual(
			List<MonitorStatus> statuses, Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select monitor from Monitor monitor where monitor.id = :id")
	Optional<Monitor> findByIdForUpdate(@Param("id") UUID id);
}
