package dev.homelabmonitor.monitor;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MonitorCheckRepository extends JpaRepository<MonitorCheck, UUID> {

	Page<MonitorCheck> findByMonitorId(UUID monitorId, Pageable pageable);
}
