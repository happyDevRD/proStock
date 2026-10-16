-- Selección de fotos por el cliente desde la web (fase 3). Ver documentacion/carpetas-ordenes.md.
-- Un enlace activo por orden; regenerarlo cambia el token (el anterior deja de servir).

CREATE TABLE IF NOT EXISTS service_order_selections (
    id                BIGSERIAL PRIMARY KEY,
    service_order_id  BIGINT       NOT NULL UNIQUE REFERENCES service_orders(id) ON DELETE CASCADE,
    token             VARCHAR(64)  NOT NULL UNIQUE,
    -- Fotos incluidas en el paquete; las de más se cobran con extra_product_id
    quota             INTEGER      NOT NULL CHECK (quota >= 0),
    extra_product_id  BIGINT       REFERENCES products(id) ON DELETE SET NULL,
    expires_at        TIMESTAMP    NOT NULL,
    -- Nombres de archivo de "2 Seleccion" marcados por el cliente (borrador o definitivo)
    selected_files    TEXT         NOT NULL DEFAULT '[]',
    submitted_at      TIMESTAMP,
    extra_count       INTEGER      NOT NULL DEFAULT 0,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
