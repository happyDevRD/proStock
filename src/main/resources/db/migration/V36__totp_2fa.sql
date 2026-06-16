-- Fase Q3: 2FA TOTP opcional por usuario

ALTER TABLE users
    ADD COLUMN totp_secret VARCHAR(255),
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT false;
