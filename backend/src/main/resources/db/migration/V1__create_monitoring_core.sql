CREATE TABLE monitors (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    type VARCHAR(16) NOT NULL,
    target VARCHAR(2048) NOT NULL,
    port INTEGER,
    enabled BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL,
    interval_seconds INTEGER NOT NULL,
    timeout_millis INTEGER NOT NULL,
    failure_threshold INTEGER NOT NULL,
    recovery_threshold INTEGER NOT NULL,
    latency_warning_millis INTEGER,
    expected_http_status INTEGER,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    consecutive_successes INTEGER NOT NULL DEFAULT 0,
    next_check_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_monitors_type CHECK (type IN ('HTTP', 'TCP')),
    CONSTRAINT ck_monitors_status CHECK (status IN ('UNKNOWN', 'ONLINE', 'DEGRADED', 'OFFLINE', 'PAUSED')),
    CONSTRAINT ck_monitors_interval CHECK (interval_seconds BETWEEN 5 AND 86400),
    CONSTRAINT ck_monitors_timeout CHECK (timeout_millis BETWEEN 100 AND 30000),
    CONSTRAINT ck_monitors_failure_threshold CHECK (failure_threshold BETWEEN 1 AND 100),
    CONSTRAINT ck_monitors_recovery_threshold CHECK (recovery_threshold BETWEEN 1 AND 100),
    CONSTRAINT ck_monitors_latency_warning CHECK (latency_warning_millis IS NULL OR latency_warning_millis BETWEEN 1 AND 30000),
    CONSTRAINT ck_monitors_expected_status CHECK (expected_http_status IS NULL OR expected_http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_monitors_type_fields CHECK (
        (type = 'HTTP' AND port IS NULL AND expected_http_status IS NOT NULL)
        OR (type = 'TCP' AND port BETWEEN 1 AND 65535 AND expected_http_status IS NULL)
    )
);

CREATE INDEX idx_monitors_due ON monitors (next_check_at) WHERE enabled = TRUE;

CREATE TABLE monitor_checks (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    result VARCHAR(32) NOT NULL,
    response_time_millis BIGINT,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    error_message VARCHAR(512),
    http_status INTEGER,
    CONSTRAINT ck_monitor_checks_result CHECK (result IN (
        'SUCCESS', 'TIMEOUT', 'DNS_FAILURE', 'CONNECTION_REFUSED', 'TLS_ERROR',
        'UNEXPECTED_STATUS', 'INVALID_TARGET', 'UNKNOWN_FAILURE'
    )),
    CONSTRAINT ck_monitor_checks_latency CHECK (response_time_millis IS NULL OR response_time_millis >= 0),
    CONSTRAINT ck_monitor_checks_http_status CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599)
);

CREATE INDEX idx_monitor_checks_monitor_time ON monitor_checks (monitor_id, checked_at DESC);

CREATE TABLE monitor_state_history (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(64) NOT NULL,
    CONSTRAINT ck_state_history_from_status CHECK (
        from_status IS NULL OR from_status IN ('UNKNOWN', 'ONLINE', 'DEGRADED', 'OFFLINE', 'PAUSED')
    ),
    CONSTRAINT ck_state_history_to_status CHECK (to_status IN ('UNKNOWN', 'ONLINE', 'DEGRADED', 'OFFLINE', 'PAUSED'))
);

CREATE INDEX idx_monitor_state_history_monitor_time
    ON monitor_state_history (monitor_id, effective_at DESC);
