-- Recepción parcial de órdenes de compra
ALTER TABLE purchase_order_items
    ADD COLUMN IF NOT EXISTS quantity_received INT NOT NULL DEFAULT 0;

UPDATE purchase_order_items poi
SET quantity_received = poi.quantity
FROM purchase_orders po
WHERE po.id = poi.purchase_order_id
  AND po.status IN ('RECEIVED', 'PARTIALLY_PAID', 'PAID');
