CREATE TABLE quotes (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customers(id),
    quote_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expiry_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    subtotal NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_itbis NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    monto_total NUMERIC(18,2) NOT NULL DEFAULT 0,
    converted_sale_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE quote_items (
    id BIGSERIAL PRIMARY KEY,
    quote_id BIGINT NOT NULL REFERENCES quotes(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(id),
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(18,2) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    discount NUMERIC(18,2) NOT NULL DEFAULT 0,
    itbis_rate NUMERIC(5,4) NOT NULL DEFAULT 0,
    subtotal NUMERIC(18,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_quotes_status ON quotes(status);
CREATE INDEX idx_quotes_customer_id ON quotes(customer_id);
CREATE INDEX idx_quotes_date ON quotes(quote_date);

-- Permisos de cotizaciones
INSERT INTO permissions (code, category, name, description) VALUES
  ('view.quotes', 'VIEW', 'Cotizaciones', 'Acceso al módulo de cotizaciones'),
  ('quotes.create', 'QUOTES', 'Crear cotizaciones', 'Permite crear nuevas cotizaciones'),
  ('quotes.edit', 'QUOTES', 'Editar cotizaciones', 'Permite editar cotizaciones existentes'),
  ('quotes.delete', 'QUOTES', 'Eliminar cotizaciones', 'Permite eliminar o cancelar cotizaciones'),
  ('quotes.convert', 'QUOTES', 'Convertir cotizaciones', 'Permite convertir una cotización en venta')
ON CONFLICT (code) DO NOTHING;

-- GESTOR: todo
INSERT INTO role_permissions (role, permission_id)
SELECT 'GESTOR', id FROM permissions WHERE code IN (
  'view.quotes','quotes.create','quotes.edit','quotes.delete','quotes.convert'
) ON CONFLICT DO NOTHING;

-- ADMIN: todo
INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions WHERE code IN (
  'view.quotes','quotes.create','quotes.edit','quotes.delete','quotes.convert'
) ON CONFLICT DO NOTHING;

-- MANAGER: ver + crear + editar + convertir
INSERT INTO role_permissions (role, permission_id)
SELECT 'MANAGER', id FROM permissions WHERE code IN (
  'view.quotes','quotes.create','quotes.edit','quotes.convert'
) ON CONFLICT DO NOTHING;

-- CASHIER: solo ver
INSERT INTO role_permissions (role, permission_id)
SELECT 'CASHIER', id FROM permissions WHERE code IN ('view.quotes') ON CONFLICT DO NOTHING;
