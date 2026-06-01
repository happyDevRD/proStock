ALTER TABLE sale_items
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS product_sku VARCHAR(100);

UPDATE sale_items si
SET product_name = p.name,
    product_sku = p.sku
FROM products p
WHERE si.product_id = p.id
  AND (si.product_name IS NULL OR si.product_name = '');
