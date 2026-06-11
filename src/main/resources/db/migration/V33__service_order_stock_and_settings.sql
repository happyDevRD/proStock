-- V33: Stock movements linked to service orders + optional stock deduction on complete

ALTER TABLE stock_movements
    ADD COLUMN IF NOT EXISTS service_order_id BIGINT REFERENCES service_orders(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_stock_movements_service_order ON stock_movements(service_order_id);

ALTER TABLE company_config
    ADD COLUMN IF NOT EXISTS service_order_deduct_stock BOOLEAN NOT NULL DEFAULT FALSE;
