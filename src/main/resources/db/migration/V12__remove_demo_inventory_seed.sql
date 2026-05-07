-- Elimina el inventario de demostracion insertado por V7 (categorias/suplidores/productos de prueba).
-- Solo afecta filas identificables por SKU o por nombres exactos de catalogo demo; no borra datos de usuario.

DELETE FROM sale_items
WHERE product_id IN (
    SELECT id FROM products
    WHERE upper(sku) IN (
        'ARZ-001', 'ACE-900', 'HAB-020', 'CAF-250', 'AGU-500',
        'REF-355', 'LEC-1LT', 'YOG-200', 'JAB-500', 'DET-900'
    )
);

DELETE FROM stock_movements
WHERE product_id IN (
    SELECT id FROM products
    WHERE upper(sku) IN (
        'ARZ-001', 'ACE-900', 'HAB-020', 'CAF-250', 'AGU-500',
        'REF-355', 'LEC-1LT', 'YOG-200', 'JAB-500', 'DET-900'
    )
);

DELETE FROM purchase_order_items
WHERE product_id IN (
    SELECT id FROM products
    WHERE upper(sku) IN (
        'ARZ-001', 'ACE-900', 'HAB-020', 'CAF-250', 'AGU-500',
        'REF-355', 'LEC-1LT', 'YOG-200', 'JAB-500', 'DET-900'
    )
);

DELETE FROM product_images
WHERE product_id IN (
    SELECT id FROM products
    WHERE upper(sku) IN (
        'ARZ-001', 'ACE-900', 'HAB-020', 'CAF-250', 'AGU-500',
        'REF-355', 'LEC-1LT', 'YOG-200', 'JAB-500', 'DET-900'
    )
);

DELETE FROM products
WHERE upper(sku) IN (
    'ARZ-001', 'ACE-900', 'HAB-020', 'CAF-250', 'AGU-500',
    'REF-355', 'LEC-1LT', 'YOG-200', 'JAB-500', 'DET-900'
);

DELETE FROM sales s
WHERE NOT EXISTS (SELECT 1 FROM sale_items si WHERE si.sale_id = s.id);

DELETE FROM purchase_orders po
WHERE NOT EXISTS (SELECT 1 FROM purchase_order_items poi WHERE poi.purchase_order_id = po.id);

DELETE FROM categories c
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.category_id = c.id)
  AND lower(c.name) IN ('abarrotes', 'bebidas', 'lacteos', 'limpieza');

DELETE FROM suppliers s
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.supplier_id = s.id)
  AND lower(s.name) IN ('distribuidora central', 'suplidora norte');
