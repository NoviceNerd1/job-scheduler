-- Migration V3: Audit logging
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    job_id          VARCHAR(64),
    action          VARCHAR(32) NOT NULL,
    performed_by    VARCHAR(128) NOT NULL,
    details         JSONB,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
