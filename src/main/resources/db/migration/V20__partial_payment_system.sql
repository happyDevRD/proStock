-- V20: Partial payment support (accounts receivable)
ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS due_date DATE;

CREATE TABLE sale_payments (
    id             BIGSERIAL PRIMARY KEY,
    sale_id        BIGINT        NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    payment_date   TIMESTAMP     NOT NULL DEFAULT NOW(),
    amount         NUMERIC(18,2) NOT NULL,
    payment_method VARCHAR(20)   NOT NULL DEFAULT 'CASH',
    notes          VARCHAR(500),
    created_by     VARCHAR(100),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sale_payments_sale_id ON sale_payments(sale_id);
