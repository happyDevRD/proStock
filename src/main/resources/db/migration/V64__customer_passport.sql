-- Clientes extranjeros: el documento puede ser un pasaporte alfanumérico (5–20) si el tipo es PASAPORTE_EXTRANJERO.
ALTER TABLE customers DROP CONSTRAINT IF EXISTS chk_customers_rnc_cedula_format;
ALTER TABLE customers ALTER COLUMN rnc_cedula TYPE VARCHAR(20);
ALTER TABLE customers ADD CONSTRAINT chk_customers_rnc_cedula_format CHECK (
    rnc_cedula IS NULL
    OR rnc_cedula ~ '^\d{9}(\d{2})?$'
    OR (tipo_identificacion = 'PASAPORTE_EXTRANJERO' AND rnc_cedula ~ '^[A-Z0-9]{5,20}$')
);
