-- Anticipos de órdenes de servicio como facturas reales (ver documentacion/anticipos-ordenes-servicio.md).

-- Tipo de venta: NORMAL (POS), DEPOSIT (anticipo de una orden) o BALANCE (factura del saldo de una orden).
ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS sale_kind VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
CREATE INDEX IF NOT EXISTS idx_sales_sale_kind ON sales(sale_kind) WHERE sale_kind <> 'NORMAL';

-- deposit_amount pasa a ser la suma de los anticipos facturados (lo mantiene el backend).
-- Los montos registrados antes de este cambio no tienen factura: se conservan aparte.
ALTER TABLE service_orders
    ADD COLUMN IF NOT EXISTS legacy_deposit_amount NUMERIC(18,2) NOT NULL DEFAULT 0;

UPDATE service_orders
SET legacy_deposit_amount = deposit_amount,
    deposit_amount = 0
WHERE deposit_amount > 0;
