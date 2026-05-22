-- Prod DB reached V15 via the v1 image. V14 in the current codebase
-- adds these columns for fresh deployments but was never applied to prod.
-- This migration ensures existing prod instances get the columns too.
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
