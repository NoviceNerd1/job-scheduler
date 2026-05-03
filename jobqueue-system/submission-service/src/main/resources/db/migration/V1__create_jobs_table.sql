-- Migration V1: Create jobs table
CREATE TABLE IF NOT EXISTS jobs (
    job_id              VARCHAR(64) PRIMARY KEY,
    job_type            VARCHAR(64) NOT NULL,
    payload             TEXT NOT NULL,
    priority            SMALLINT NOT NULL CHECK (priority BETWEEN 1 AND 5),
    status              VARCHAR(20) NOT NULL,
    attempts            SMALLINT DEFAULT 0,
    max_retries         SMALLINT DEFAULT 3,
    next_retry_at       TIMESTAMP,
    timeout_ms          BIGINT DEFAULT 30000,
    leased_by           VARCHAR(128),
    lease_expires_at    TIMESTAMP,
    created_by          VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(256),
    last_error          TEXT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP
);

CREATE INDEX idx_jobs_status_next_retry ON jobs (status, next_retry_at) 
    WHERE status IN ('PENDING', 'RETRY');
    
CREATE INDEX idx_jobs_idempotency ON jobs (idempotency_key) 
    WHERE idempotency_key IS NOT NULL;
