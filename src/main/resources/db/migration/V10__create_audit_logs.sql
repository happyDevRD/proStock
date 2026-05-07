CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(100),
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    details TEXT
);

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
