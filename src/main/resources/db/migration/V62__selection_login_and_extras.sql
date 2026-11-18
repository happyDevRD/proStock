-- Selección de fotos v2 (documentacion/carpetas-ordenes.md, «Fase 3.1»):
--  * el cliente entra con usuario y contraseña (ya no basta el enlace)
--  * las fotos de más quedan como adicionales pendientes hasta que el estudio las autoriza (con el pago cubierto)

ALTER TABLE service_order_selections
    ADD COLUMN IF NOT EXISTS username          VARCHAR(60),
    ADD COLUMN IF NOT EXISTS password_hash     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS failed_attempts   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until      TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_login_at     TIMESTAMP,
    -- NONE: sin adicionales · PENDING: esperan autorización · APPROVED · REJECTED
    ADD COLUMN IF NOT EXISTS extra_status      VARCHAR(20) NOT NULL DEFAULT 'NONE',
    -- JSON: nombres de las previas adicionales (las que pasan del cupo)
    ADD COLUMN IF NOT EXISTS extra_files       TEXT        NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS extra_item_id     BIGINT,
    ADD COLUMN IF NOT EXISTS extra_decided_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS extra_decided_by  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS extra_notes       VARCHAR(500);

CREATE UNIQUE INDEX IF NOT EXISTS uq_service_order_selections_username
    ON service_order_selections (LOWER(username)) WHERE username IS NOT NULL;
