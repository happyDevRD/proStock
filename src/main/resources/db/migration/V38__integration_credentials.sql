-- Fase Q3: almacenamiento cifrado de credenciales de integraciones (WhatsApp, pagos, etc.)

CREATE TABLE integration_credentials (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    credential_key VARCHAR(128) NOT NULL,
    encrypted_value TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_integration_credential UNIQUE (provider, credential_key)
);
