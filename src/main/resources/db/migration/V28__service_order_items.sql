-- V28: Products/packages linked to a service order
-- Allows pre-loading items into the POS invoice from the order.
CREATE TABLE service_order_items (
    id               BIGSERIAL      PRIMARY KEY,
    service_order_id BIGINT         NOT NULL REFERENCES service_orders(id) ON DELETE CASCADE,
    product_id       BIGINT         NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity         INTEGER        NOT NULL DEFAULT 1,
    unit_price       NUMERIC(18,2)  NOT NULL,
    notes            VARCHAR(500),
    position         INTEGER        NOT NULL DEFAULT 0,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_so_items_order_id ON service_order_items(service_order_id);
