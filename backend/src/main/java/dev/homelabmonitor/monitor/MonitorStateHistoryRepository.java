package dev.homelabmonitor.monitor;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MonitorStateHistoryRepository extends JpaRepository<MonitorStateHistory, UUID> {

	Page<MonitorStateHistory> findByMonitorId(UUID monitorId, Pageable pageable);
}
