ALTER TABLE monitors ADD COLUMN last_checked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE monitors ADD COLUMN observation_valid_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE monitor_checks ADD COLUMN observation_valid_until TIMESTAMP WITH TIME ZONE;

UPDATE monitors monitor
SET last_checked_at = (
    SELECT MAX(checked_at) FROM monitor_checks check_result WHERE check_result.monitor_id = monitor.id
);

UPDATE monitor_checks check_result
SET observation_valid_until = check_result.checked_at;

ALTER TABLE monitor_checks ALTER COLUMN observation_valid_until SET NOT NULL;

UPDATE monitors monitor
SET observation_valid_until = monitor.last_checked_at
WHERE monitor.last_checked_at IS NOT NULL;

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    outage_reason VARCHAR(32) NOT NULL,
    resolution_reason VARCHAR(32),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_incidents_status CHECK (status IN ('ACTIVE', 'RESOLVED')),
    CONSTRAINT ck_incidents_outage_reason CHECK (outage_reason IN (
        'TIMEOUT', 'DNS_FAILURE', 'CONNECTION_REFUSED', 'TLS_ERROR',
        'UNEXPECTED_STATUS', 'INVALID_TARGET', 'UNKNOWN_FAILURE'
    )),
    CONSTRAINT ck_incidents_resolution_reason CHECK (
        resolution_reason IS NULL OR resolution_reason IN ('RECOVERED', 'MONITORING_PAUSED')
    ),
    CONSTRAINT ck_incidents_lifecycle CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL AND resolution_reason IS NULL)
        OR (status = 'RESOLVED' AND ended_at IS NOT NULL AND resolution_reason IS NOT NULL)
    ),
    CONSTRAINT ck_incidents_time_order CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE UNIQUE INDEX uk_incidents_active_monitor ON incidents (monitor_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_incidents_started_at ON incidents (started_at DESC);
CREATE INDEX idx_incidents_monitor_started_at ON incidents (monitor_id, started_at DESC);

INSERT INTO incidents (id, monitor_id, status, outage_reason, started_at)
SELECT
    gen_random_uuid(),
    monitor.id,
    'ACTIVE',
    COALESCE(trigger_check.result, 'UNKNOWN_FAILURE'),
    COALESCE(offline_transition.effective_at, monitor.updated_at)
FROM monitors monitor
LEFT JOIN LATERAL (
    SELECT history.effective_at
    FROM monitor_state_history history
    WHERE history.monitor_id = monitor.id AND history.to_status = 'OFFLINE'
    ORDER BY history.effective_at DESC, history.id DESC
    LIMIT 1
) offline_transition ON TRUE
LEFT JOIN LATERAL (
    SELECT check_result.result
    FROM monitor_checks check_result
    WHERE check_result.monitor_id = monitor.id
      AND check_result.result <> 'SUCCESS'
      AND check_result.checked_at = offline_transition.effective_at
    ORDER BY check_result.id DESC
    LIMIT 1
) trigger_check ON TRUE
WHERE monitor.status = 'OFFLINE';
