BEGIN;

-- Catalogos base
INSERT INTO categories (name) VALUES
  ('Electronics'),
  ('Accessories'),
  ('Services')
ON CONFLICT (name) DO NOTHING;

INSERT INTO suppliers (name, contact_name, contact_email, phone, address) VALUES
  ('Tech Distribuciones SRL', 'Laura Perez', 'compras@techdist.do', '809-555-0101', 'Santo Domingo'),
  ('Global Imports DO', 'Carlos Gomez', 'ventas@globalimports.do', '809-555-0102', 'Santiago')
ON CONFLICT (name) DO UPDATE
SET contact_name = EXCLUDED.contact_name,
    contact_email = EXCLUDED.contact_email,
    phone = EXCLUDED.phone,
    address = EXCLUDED.address;

-- Configuracion de empresa (solo una fila)
INSERT INTO company_config (
  rnc, razon_social, nombre_comercial, direccion,
  municipio_codigo, provincia_codigo, actividad_economica,
  numero_telefono, correo_electronico
) VALUES (
  '13245678901', 'ProStock Demo SRL', 'ProStock Demo',
  'Av. Winston Churchill 101',
  '010101', '010100', 'Comercio al por menor',
  '8095550199', 'admin@prostockdemo.do'
)
ON CONFLICT (rnc) DO UPDATE
SET razon_social = EXCLUDED.razon_social,
    nombre_comercial = EXCLUDED.nombre_comercial,
    direccion = EXCLUDED.direccion,
    municipio_codigo = EXCLUDED.municipio_codigo,
    provincia_codigo = EXCLUDED.provincia_codigo,
    actividad_economica = EXCLUDED.actividad_economica,
    numero_telefono = EXCLUDED.numero_telefono,
    correo_electronico = EXCLUDED.correo_electronico;

-- Secuencias NCF
INSERT INTO ncf_sequences (tipo_comprobante, prefijo, valor_actual, valor_final, fecha_vencimiento) VALUES
  ('31', 'E', 310000000001, 310000009999, DATE '2030-12-31'),
  ('32', 'E', 320000000001, 320000009999, DATE '2030-12-31')
ON CONFLICT (tipo_comprobante) DO UPDATE
SET prefijo = EXCLUDED.prefijo,
    valor_actual = EXCLUDED.valor_actual,
    valor_final = EXCLUDED.valor_final,
    fecha_vencimiento = EXCLUDED.fecha_vencimiento;

-- Clientes demo
INSERT INTO customers (first_name, last_name, email, phone_number, address, rnc_cedula, tipo_identificacion) VALUES
  ('Juan', 'Rodriguez', 'juan.rodriguez@demo.do', '809-555-0201', 'Piantini, Santo Domingo', '10198765432', 'CEDULA'),
  ('Maria', 'Santos', 'maria.santos@demo.do', '809-555-0202', 'La Julia, Santo Domingo', '131765432', 'RNC'),
  ('Pedro', 'Fernandez', 'pedro.fernandez@demo.do', '829-555-0203', 'Santiago Centro', '40212345678', 'CEDULA')
ON CONFLICT (email) DO UPDATE
SET first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    phone_number = EXCLUDED.phone_number,
    address = EXCLUDED.address,
    rnc_cedula = EXCLUDED.rnc_cedula,
    tipo_identificacion = EXCLUDED.tipo_identificacion;

-- Productos demo
INSERT INTO products (
  sku, name, description, category_id, supplier_id,
  cost_price, selling_price, stock, min_stock, expiration_date,
  for_sale, indicador_facturacion, tipo_bien_servicio, unit_of_measure,
  location, weight, length, width, height, status, barcode, tax_rate
) VALUES (
  'EL-9021', 'ZenPhone Pro Max 256GB', 'Smartphone de alta gama',
  (SELECT id FROM categories WHERE name = 'Electronics'),
  (SELECT id FROM suppliers WHERE name = 'Tech Distribuciones SRL'),
  35000.00, 45000.00, 42, 10, NULL,
  TRUE, 1, 1, 1, 'A-01', 0.20, 16.0, 7.2, 0.8, 'ACTIVE', '750100000001', 0.18
)
ON CONFLICT (sku) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  category_id = EXCLUDED.category_id,
  supplier_id = EXCLUDED.supplier_id,
  cost_price = EXCLUDED.cost_price,
  selling_price = EXCLUDED.selling_price,
  stock = EXCLUDED.stock,
  min_stock = EXCLUDED.min_stock,
  expiration_date = EXCLUDED.expiration_date,
  for_sale = EXCLUDED.for_sale,
  indicador_facturacion = EXCLUDED.indicador_facturacion,
  tipo_bien_servicio = EXCLUDED.tipo_bien_servicio,
  unit_of_measure = EXCLUDED.unit_of_measure,
  location = EXCLUDED.location,
  weight = EXCLUDED.weight,
  length = EXCLUDED.length,
  width = EXCLUDED.width,
  height = EXCLUDED.height,
  status = EXCLUDED.status,
  barcode = EXCLUDED.barcode,
  tax_rate = EXCLUDED.tax_rate;

INSERT INTO products (
  sku, name, description, category_id, supplier_id,
  cost_price, selling_price, stock, min_stock, expiration_date,
  for_sale, indicador_facturacion, tipo_bien_servicio, unit_of_measure,
  location, weight, length, width, height, status, barcode, tax_rate
) VALUES (
  'AC-1104', 'Noise Cancelling Headphones V2', 'Auriculares premium',
  (SELECT id FROM categories WHERE name = 'Accessories'),
  (SELECT id FROM suppliers WHERE name = 'Global Imports DO'),
  8000.00, 12500.00, 8, 5, NULL,
  TRUE, 1, 1, 1, 'B-02', 0.30, 18.0, 17.0, 8.0, 'ACTIVE', '750100000002', 0.18
)
ON CONFLICT (sku) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  category_id = EXCLUDED.category_id,
  supplier_id = EXCLUDED.supplier_id,
  cost_price = EXCLUDED.cost_price,
  selling_price = EXCLUDED.selling_price,
  stock = EXCLUDED.stock,
  min_stock = EXCLUDED.min_stock,
  expiration_date = EXCLUDED.expiration_date,
  for_sale = EXCLUDED.for_sale,
  indicador_facturacion = EXCLUDED.indicador_facturacion,
  tipo_bien_servicio = EXCLUDED.tipo_bien_servicio,
  unit_of_measure = EXCLUDED.unit_of_measure,
  location = EXCLUDED.location,
  weight = EXCLUDED.weight,
  length = EXCLUDED.length,
  width = EXCLUDED.width,
  height = EXCLUDED.height,
  status = EXCLUDED.status,
  barcode = EXCLUDED.barcode,
  tax_rate = EXCLUDED.tax_rate;

INSERT INTO products (
  sku, name, description, category_id, supplier_id,
  cost_price, selling_price, stock, min_stock, expiration_date,
  for_sale, indicador_facturacion, tipo_bien_servicio, unit_of_measure,
  location, weight, length, width, height, status, barcode, tax_rate
) VALUES (
  'LT-5001', 'UltraBook Studio 15 M2', 'Laptop profesional',
  (SELECT id FROM categories WHERE name = 'Electronics'),
  (SELECT id FROM suppliers WHERE name = 'Tech Distribuciones SRL'),
  72000.00, 89000.00, 15, 4, NULL,
  TRUE, 1, 1, 1, 'A-03', 1.40, 35.0, 24.0, 1.8, 'ACTIVE', '750100000003', 0.18
)
ON CONFLICT (sku) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  category_id = EXCLUDED.category_id,
  supplier_id = EXCLUDED.supplier_id,
  cost_price = EXCLUDED.cost_price,
  selling_price = EXCLUDED.selling_price,
  stock = EXCLUDED.stock,
  min_stock = EXCLUDED.min_stock,
  expiration_date = EXCLUDED.expiration_date,
  for_sale = EXCLUDED.for_sale,
  indicador_facturacion = EXCLUDED.indicador_facturacion,
  tipo_bien_servicio = EXCLUDED.tipo_bien_servicio,
  unit_of_measure = EXCLUDED.unit_of_measure,
  location = EXCLUDED.location,
  weight = EXCLUDED.weight,
  length = EXCLUDED.length,
  width = EXCLUDED.width,
  height = EXCLUDED.height,
  status = EXCLUDED.status,
  barcode = EXCLUDED.barcode,
  tax_rate = EXCLUDED.tax_rate;

INSERT INTO products (
  sku, name, description, category_id, supplier_id,
  cost_price, selling_price, stock, min_stock, expiration_date,
  for_sale, indicador_facturacion, tipo_bien_servicio, unit_of_measure,
  location, weight, length, width, height, status, barcode, tax_rate
) VALUES (
  'SV-001', 'Servicio de diagnostico', 'Servicio tecnico',
  (SELECT id FROM categories WHERE name = 'Services'),
  (SELECT id FROM suppliers WHERE name = 'Global Imports DO'),
  1000.00, 2500.00, 9999, 0, NULL,
  TRUE, 4, 2, 1, 'SERV', NULL, NULL, NULL, NULL, 'ACTIVE', '750100000004', 0.00
)
ON CONFLICT (sku) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  category_id = EXCLUDED.category_id,
  supplier_id = EXCLUDED.supplier_id,
  cost_price = EXCLUDED.cost_price,
  selling_price = EXCLUDED.selling_price,
  stock = EXCLUDED.stock,
  min_stock = EXCLUDED.min_stock,
  expiration_date = EXCLUDED.expiration_date,
  for_sale = EXCLUDED.for_sale,
  indicador_facturacion = EXCLUDED.indicador_facturacion,
  tipo_bien_servicio = EXCLUDED.tipo_bien_servicio,
  unit_of_measure = EXCLUDED.unit_of_measure,
  location = EXCLUDED.location,
  weight = EXCLUDED.weight,
  length = EXCLUDED.length,
  width = EXCLUDED.width,
  height = EXCLUDED.height,
  status = EXCLUDED.status,
  barcode = EXCLUDED.barcode,
  tax_rate = EXCLUDED.tax_rate;

-- Imagenes demo para productos principales
INSERT INTO product_images (file_name, content_type, product_id)
SELECT 'seed-phone.jpg', 'image/jpeg', id FROM products WHERE sku = 'EL-9021'
ON CONFLICT DO NOTHING;

INSERT INTO product_images (file_name, content_type, product_id)
SELECT 'seed-laptop.jpg', 'image/jpeg', id FROM products WHERE sku = 'LT-5001'
ON CONFLICT DO NOTHING;

-- Orden de compra pendiente demo
WITH supplier_ref AS (
  SELECT id AS supplier_id FROM suppliers WHERE name = 'Tech Distribuciones SRL'
), upsert_po AS (
  INSERT INTO purchase_orders (supplier_id, order_date, reception_date, status)
  SELECT supplier_ref.supplier_id, CURRENT_DATE, NULL, 'PENDING' FROM supplier_ref
  RETURNING id
)
INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, unit_price)
SELECT upsert_po.id, p.id, 10, p.cost_price::float
FROM upsert_po
JOIN products p ON p.sku IN ('EL-9021', 'AC-1104');

-- Ventas demo (una completada y una pendiente)
WITH customer_ref AS (
  SELECT id FROM customers WHERE email = 'juan.rodriguez@demo.do'
), completed_sale AS (
  INSERT INTO sales (
    sale_date, customer_id, status, tipo_comprobante, ncf,
    monto_gravado_total, monto_exento, total_itbis, monto_total,
    tipo_ingresos, fecha_firma, codigo_seguridad, qr_payload_url, qr_code_base64
  )
  SELECT
    NOW() - INTERVAL '1 day',
    customer_ref.id,
    'COMPLETED',
    '31',
    'E310000000900',
    57500.00,
    0.00,
    10350.00,
    67850.00,
    '01',
    NOW() - INTERVAL '1 day',
    'ABC123',
    'https://dgii.gov.do/verify/demo-900',
    ''
  FROM customer_ref
  ON CONFLICT (ncf) DO UPDATE
  SET sale_date = EXCLUDED.sale_date,
      customer_id = EXCLUDED.customer_id,
      status = EXCLUDED.status,
      tipo_comprobante = EXCLUDED.tipo_comprobante,
      monto_gravado_total = EXCLUDED.monto_gravado_total,
      monto_exento = EXCLUDED.monto_exento,
      total_itbis = EXCLUDED.total_itbis,
      monto_total = EXCLUDED.monto_total,
      tipo_ingresos = EXCLUDED.tipo_ingresos,
      fecha_firma = EXCLUDED.fecha_firma,
      codigo_seguridad = EXCLUDED.codigo_seguridad,
      qr_payload_url = EXCLUDED.qr_payload_url,
      qr_code_base64 = EXCLUDED.qr_code_base64
  RETURNING id
), pending_sale AS (
  INSERT INTO sales (
    sale_date, customer_id, status, tipo_comprobante, ncf,
    monto_gravado_total, monto_exento, total_itbis, monto_total,
    tipo_ingresos, fecha_firma, codigo_seguridad, qr_payload_url, qr_code_base64
  )
  SELECT
    NOW(),
    customer_ref.id,
    'PENDING',
    '32',
    'E320000000901',
    2500.00,
    0.00,
    450.00,
    2950.00,
    '01',
    NULL,
    NULL,
    NULL,
    NULL
  FROM customer_ref
  ON CONFLICT (ncf) DO UPDATE
  SET sale_date = EXCLUDED.sale_date,
      customer_id = EXCLUDED.customer_id,
      status = EXCLUDED.status,
      tipo_comprobante = EXCLUDED.tipo_comprobante,
      monto_gravado_total = EXCLUDED.monto_gravado_total,
      monto_exento = EXCLUDED.monto_exento,
      total_itbis = EXCLUDED.total_itbis,
      monto_total = EXCLUDED.monto_total,
      tipo_ingresos = EXCLUDED.tipo_ingresos
  RETURNING id
)
DELETE FROM sale_items
WHERE sale_id IN (
  SELECT id FROM sales WHERE ncf IN ('E310000000900', 'E320000000901')
);

INSERT INTO sale_items (sale_id, product_id, quantity, unit_price)
SELECT s.id, p.id, 1, p.selling_price
FROM sales s
JOIN products p ON p.sku = 'EL-9021'
WHERE s.ncf = 'E310000000900';

INSERT INTO sale_items (sale_id, product_id, quantity, unit_price)
SELECT s.id, p.id, 1, p.selling_price
FROM sales s
JOIN products p ON p.sku = 'AC-1104'
WHERE s.ncf = 'E310000000900';

INSERT INTO sale_items (sale_id, product_id, quantity, unit_price)
SELECT s.id, p.id, 1, p.selling_price
FROM sales s
JOIN products p ON p.sku = 'SV-001'
WHERE s.ncf = 'E320000000901';

-- Movimientos de stock demo asociados a venta completada
DELETE FROM stock_movements WHERE reason LIKE 'SEED:%';

INSERT INTO stock_movements (
  product_id, movement_date, quantity_change, type, reason, sale_id,
  stock_before, stock_after
)
SELECT p.id, NOW() - INTERVAL '1 day', -1, 'OUT', 'SEED: completed sale', s.id, p.stock + 1, p.stock
FROM products p
JOIN sales s ON s.ncf = 'E310000000900'
WHERE p.sku IN ('EL-9021', 'AC-1104');

COMMIT;
