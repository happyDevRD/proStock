-- Categorias base
INSERT INTO categories (name)
SELECT v.name
FROM (
    VALUES
        ('Abarrotes'),
        ('Bebidas'),
        ('Lacteos'),
        ('Limpieza')
) AS v(name)
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE lower(c.name) = lower(v.name)
);

-- Suplidores base
INSERT INTO suppliers (name, contact_name, contact_email, phone, address)
SELECT v.name, v.contact_name, v.contact_email, v.phone, v.address
FROM (
    VALUES
        ('Distribuidora Central', 'Ventas Central', 'ventas@central.do', '809-555-0101', 'Santo Domingo'),
        ('Suplidora Norte', 'Atencion Norte', 'soporte@norte.do', '809-555-0102', 'Santiago')
) AS v(name, contact_name, contact_email, phone, address)
WHERE NOT EXISTS (
    SELECT 1 FROM suppliers s WHERE lower(s.name) = lower(v.name)
);

-- Productos por defecto (10)
INSERT INTO products (
    sku, name, description, category_id, supplier_id, cost_price, selling_price,
    stock, min_stock, for_sale, indicador_facturacion, tipo_bien_servicio,
    unit_of_measure, status, barcode, tax_rate
)
SELECT
    v.sku,
    v.name,
    v.description,
    c.id,
    s.id,
    v.cost_price,
    v.selling_price,
    v.stock,
    v.min_stock,
    TRUE,
    1,
    1,
    58,
    'ACTIVE',
    v.barcode,
    0.18
FROM (
    VALUES
        ('ARZ-001', 'Arroz Selecto 1kg', 'Arroz premium de grano largo', 'Abarrotes', 'Distribuidora Central', 68.00, 85.00, 120, 25, '7501000000011'),
        ('ACE-900', 'Aceite Vegetal 900ml', 'Aceite vegetal refinado', 'Abarrotes', 'Distribuidora Central', 172.00, 210.00, 74, 16, '7501000000028'),
        ('HAB-020', 'Habichuelas Rojas 400g', 'Habichuelas empacadas al vacío', 'Abarrotes', 'Suplidora Norte', 51.00, 65.00, 90, 20, '7501000000035'),
        ('CAF-250', 'Cafe Molido 250g', 'Café tostado molido', 'Abarrotes', 'Suplidora Norte', 114.00, 145.00, 48, 12, '7501000000042'),
        ('AGU-500', 'Agua Purificada 500ml', 'Botella PET', 'Bebidas', 'Distribuidora Central', 22.00, 35.00, 220, 50, '7501000000059'),
        ('REF-355', 'Refresco Cola 355ml', 'Bebida carbonatada', 'Bebidas', 'Suplidora Norte', 40.00, 55.00, 140, 30, '7501000000066'),
        ('LEC-1LT', 'Leche Entera 1L', 'Leche UHT larga duración', 'Lacteos', 'Distribuidora Central', 62.00, 79.00, 65, 18, '7501000000073'),
        ('YOG-200', 'Yogurt Fresa 200g', 'Yogurt natural sabor fresa', 'Lacteos', 'Suplidora Norte', 29.00, 42.00, 80, 20, '7501000000080'),
        ('JAB-500', 'Jabon Liquido 500ml', 'Jabón líquido antibacterial', 'Limpieza', 'Distribuidora Central', 70.00, 98.00, 34, 10, '7501000000097'),
        ('DET-900', 'Detergente 900g', 'Detergente en polvo multiuso', 'Limpieza', 'Suplidora Norte', 88.00, 120.00, 42, 12, '7501000000103')
) AS v(sku, name, description, category_name, supplier_name, cost_price, selling_price, stock, min_stock, barcode)
JOIN categories c ON lower(c.name) = lower(v.category_name)
JOIN suppliers s ON lower(s.name) = lower(v.supplier_name)
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE upper(p.sku) = upper(v.sku)
);

-- Imagen placeholder para productos base sin imagen
INSERT INTO product_images (file_name, content_type, product_id)
SELECT 'placeholder-product.png', 'image/png', p.id
FROM products p
WHERE upper(p.sku) IN (
    'ARZ-001','ACE-900','HAB-020','CAF-250','AGU-500',
    'REF-355','LEC-1LT','YOG-200','JAB-500','DET-900'
)
AND NOT EXISTS (
    SELECT 1 FROM product_images pi WHERE pi.product_id = p.id
);
