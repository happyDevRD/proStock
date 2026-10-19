-- Neto recibido ya contabilizado de cada orden de compra. Desde ahora cada recepción
-- asienta solo lo recibido que falta por contabilizar (ver PurchaseReceptionAccounting).
ALTER TABLE purchase_orders
    ADD COLUMN accounted_received_net NUMERIC(18, 2) NOT NULL DEFAULT 0;

-- Las órdenes que la sincronización contable anterior ya asentó lo hicieron por el neto
-- total pedido: se marcan así para no volver a contabilizarlas.
UPDATE purchase_orders po
SET accounted_received_net = (
    SELECT COALESCE(SUM(ROUND(CAST(i.unit_price AS NUMERIC) * i.quantity, 2)), 0)
    FROM purchase_order_items i
    WHERE i.purchase_order_id = po.id
)
WHERE EXISTS (
    SELECT 1 FROM acc_sync_log l
    WHERE l.entity_type = 'PURCHASE_ORDER' AND l.entity_id = po.id
);
