ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS service_order_id BIGINT
        REFERENCES service_orders(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_expenses_service_order_id ON expenses(service_order_id);
