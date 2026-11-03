-- Productos internos que crea el backend (p. ej. para facturar anticipos y saldos de órdenes
-- de servicio). No se muestran en el POS ni en el inventario, pero siguen en ventas y reportes.
ALTER TABLE products ADD COLUMN system_item BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE products SET system_item = TRUE WHERE sku IN ('SYS-ANTICIPO-OS', 'SYS-SALDO-OS');
