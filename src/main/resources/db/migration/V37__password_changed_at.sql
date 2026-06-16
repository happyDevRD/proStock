-- Fase Q3: política de contraseñas — registra cuándo se cambió por última vez

ALTER TABLE users
    ADD COLUMN password_changed_at TIMESTAMP WITH TIME ZONE;
