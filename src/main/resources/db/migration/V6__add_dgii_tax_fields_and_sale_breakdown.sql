ALTER TABLE products
    ADD COLUMN IF NOT EXISTS indicador_facturacion SMALLINT NOT NULL DEFAULT 4,
    ADD COLUMN IF NOT EXISTS tipo_bien_servicio SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS chk_products_indicador_facturacion;

ALTER TABLE products
    ADD CONSTRAINT chk_products_indicador_facturacion CHECK (indicador_facturacion IN (1, 2, 3, 4));

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS chk_products_tipo_bien_servicio;

ALTER TABLE products
    ADD CONSTRAINT chk_products_tipo_bien_servicio CHECK (tipo_bien_servicio IN (1, 2));

ALTER TABLE products
    ALTER COLUMN unit_of_measure TYPE INTEGER USING
        CASE
            WHEN unit_of_measure ~ '^\d+$' THEN unit_of_measure::INTEGER
            ELSE 43
        END;

ALTER TABLE products
    ALTER COLUMN unit_of_measure SET DEFAULT 43,
    ALTER COLUMN unit_of_measure SET NOT NULL;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS rnc_cedula VARCHAR(11),
    ADD COLUMN IF NOT EXISTS tipo_identificacion VARCHAR(30);

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS chk_customers_rnc_cedula_format;

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_rnc_cedula_format
        CHECK (rnc_cedula IS NULL OR rnc_cedula ~ '^\d{9}(\d{2})?$');

ALTER TABLE customers
    DROP CONSTRAINT IF EXISTS chk_customers_tipo_identificacion;

ALTER TABLE customers
    ADD CONSTRAINT chk_customers_tipo_identificacion
        CHECK (tipo_identificacion IS NULL OR tipo_identificacion IN ('RNC', 'CEDULA', 'PASAPORTE_EXTRANJERO'));

ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS monto_gravado_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS monto_exento NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_itbis NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS monto_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tipo_ingresos VARCHAR(2) NOT NULL DEFAULT '01',
    ADD COLUMN IF NOT EXISTS fecha_firma TIMESTAMP,
    ADD COLUMN IF NOT EXISTS codigo_seguridad VARCHAR(64),
    ADD COLUMN IF NOT EXISTS qr_payload_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS qr_code_base64 TEXT;

ALTER TABLE sales
    DROP CONSTRAINT IF EXISTS chk_sales_tipo_ingresos;

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_tipo_ingresos CHECK (tipo_ingresos IN ('01'));

ALTER TABLE sale_items
    ALTER COLUMN unit_price TYPE NUMERIC(18, 2) USING ROUND(unit_price::NUMERIC, 2);
