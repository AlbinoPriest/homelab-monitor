ALTER TABLE monitors DROP CONSTRAINT ck_monitors_interval;

UPDATE monitors
SET interval_seconds = 60,
    next_check_at = CASE
        WHEN enabled THEN LEAST(COALESCE(next_check_at, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)
        ELSE NULL
    END
WHERE interval_seconds < 60;

ALTER TABLE monitors ADD CONSTRAINT ck_monitors_interval
    CHECK (interval_seconds BETWEEN 60 AND 86400);

CREATE INDEX idx_monitor_checks_reachable_latency
    ON monitor_checks (checked_at, monitor_id) INCLUDE (response_time_millis)
    WHERE result IN ('SUCCESS', 'UNEXPECTED_STATUS') AND response_time_millis IS NOT NULL;

DROP INDEX idx_monitor_state_history_monitor_time;
CREATE INDEX idx_monitor_state_history_monitor_time
    ON monitor_state_history (monitor_id, effective_at DESC, id DESC) INCLUDE (to_status);
CREATE INDEX idx_monitor_state_history_effective_at
    ON monitor_state_history (effective_at, monitor_id, id) INCLUDE (to_status);
