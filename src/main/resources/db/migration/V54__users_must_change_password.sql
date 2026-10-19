-- Contraseña temporal: el usuario debe cambiarla al entrar. La activa quien crea el usuario
-- o le pone una contraseña nueva; se apaga cuando el propio usuario la cambia.
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
