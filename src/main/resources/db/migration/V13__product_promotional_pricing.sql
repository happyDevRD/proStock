-- Precios especiales y descuentos por producto/servicio (vigencia opcional por fechas).
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS promo_price NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS promo_percent_off NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS promo_start_date DATE,
    ADD COLUMN IF NOT EXISTS promo_end_date DATE;
