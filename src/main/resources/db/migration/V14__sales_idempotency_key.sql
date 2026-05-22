ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_idempotency_key
    ON sales (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_idempotency_key
    ON customers (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
