-- V32: Notas de crédito / devoluciones parciales
ALTER TABLE sale_items
    ADD COLUMN IF NOT EXISTS quantity_returned INT NOT NULL DEFAULT 0;

CREATE TABLE credit_notes (
    id                  BIGSERIAL PRIMARY KEY,
    sale_id             BIGINT        NOT NULL REFERENCES sales(id),
    ncf                 VARCHAR(13),
    tipo_comprobante    VARCHAR(2)    NOT NULL DEFAULT '34',
    ncf_modificado      VARCHAR(13),
    reason              VARCHAR(500),
    monto_gravado_total NUMERIC(18,2) NOT NULL DEFAULT 0,
    monto_exento        NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_itbis         NUMERIC(18,2) NOT NULL DEFAULT 0,
    monto_total         NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100)
);

CREATE TABLE credit_note_items (
    id             BIGSERIAL PRIMARY KEY,
    credit_note_id BIGINT        NOT NULL REFERENCES credit_notes(id) ON DELETE CASCADE,
    sale_item_id   BIGINT        NOT NULL REFERENCES sale_items(id),
    product_id     BIGINT        NOT NULL REFERENCES products(id),
    quantity       INT           NOT NULL,
    unit_price     NUMERIC(18,2) NOT NULL,
    product_name   VARCHAR(255),
    product_sku    VARCHAR(100)
);

CREATE INDEX idx_credit_notes_sale_id ON credit_notes(sale_id);
