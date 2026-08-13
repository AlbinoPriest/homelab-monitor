package dev.homelabmonitor.monitor;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

interface MonitorCheckRepository extends JpaRepository<MonitorCheck, UUID> {

	Page<MonitorCheck> findByMonitorId(UUID monitorId, Pageable pageable);

	@Modifying
	@Transactional
	@Query(value = """
			DELETE FROM monitor_checks WHERE id IN (
			  SELECT id FROM monitor_checks WHERE observation_valid_until < :cutoff
			  ORDER BY checked_at LIMIT :batchSize
			)
			""", nativeQuery = true)
	int deleteRetentionBatch(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
