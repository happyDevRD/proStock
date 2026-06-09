-- V24: Supplier payment system (Cuentas por Pagar)

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(18,2) NOT NULL DEFAULT 0;

CREATE TABLE supplier_payments (
    id                BIGSERIAL     PRIMARY KEY,
    purchase_order_id BIGINT        NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    payment_date      TIMESTAMP     NOT NULL DEFAULT NOW(),
    amount            NUMERIC(18,2) NOT NULL,
    payment_method    VARCHAR(20)   NOT NULL DEFAULT 'CASH',
    notes             VARCHAR(500),
    created_by        VARCHAR(100),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_supplier_payments_po_id ON supplier_payments(purchase_order_id);
