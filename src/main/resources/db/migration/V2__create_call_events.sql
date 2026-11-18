CREATE TABLE call_events (
    call_id VARCHAR(128) NOT NULL PRIMARY KEY,
    caller_number VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    agent_name VARCHAR(100),
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_call_events_started_at (started_at)
);
