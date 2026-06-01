CREATE TABLE IF NOT EXISTS company_module_config (
    id BIGSERIAL PRIMARY KEY,
    module VARCHAR(30) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO company_module_config (module, enabled)
VALUES
    ('POS', TRUE),
    ('INVOICE', TRUE),
    ('INVENTORY', TRUE),
    ('SUPPLIERS', TRUE),
    ('REPORTS', TRUE),
    ('ACCOUNTING', TRUE)
ON CONFLICT (module) DO NOTHING;
