-- Fase C1: datos necesarios para el reporte DGII 606 (compras)

ALTER TABLE suppliers
    ADD COLUMN rnc_cedula VARCHAR(20),
    ADD COLUMN tipo_identificacion VARCHAR(30);

ALTER TABLE purchase_orders
    ADD COLUMN ncf_proveedor VARCHAR(19),
    ADD COLUMN tipo_bienes_servicios VARCHAR(2) NOT NULL DEFAULT '09',
    ADD COLUMN total_itbis NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN fecha_pago DATE,
    ADD COLUMN payment_method VARCHAR(20);

COMMENT ON COLUMN purchase_orders.tipo_bienes_servicios IS
    'Código DGII 606 "tipo de bienes y servicios comprados" (01-11); 09 = forma parte del costo de venta';
