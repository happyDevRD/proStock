-- V26: Link sales to service orders (bidirectional tracking)

ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS service_order_id BIGINT REFERENCES service_orders(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sales_service_order ON sales(service_order_id);
