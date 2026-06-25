-- F24: Módulo de Gastos Directos
-- Gastos sin orden de compra: caja chica, servicios, alquileres, etc.
-- Incluidos en el reporte DGII 606 cuando tienen NCF de proveedor.

CREATE TABLE expenses (
    id               BIGSERIAL PRIMARY KEY,
    description      VARCHAR(255) NOT NULL,
    amount           NUMERIC(18,2) NOT NULL DEFAULT 0,
    itbis            NUMERIC(18,2) NOT NULL DEFAULT 0,
    expense_date     DATE NOT NULL,
    payment_date     DATE,
    payment_method   VARCHAR(20),
    category         VARCHAR(50)  NOT NULL DEFAULT 'OTHER',
    supplier_id      BIGINT REFERENCES suppliers(id) ON DELETE SET NULL,
    ncf_proveedor    VARCHAR(19),
    tipo_bienes_servicios VARCHAR(2) NOT NULL DEFAULT '09',
    rnc_cedula       VARCHAR(20),
    tipo_identificacion VARCHAR(20),
    notes            TEXT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expenses_date        ON expenses(expense_date);
CREATE INDEX idx_expenses_category    ON expenses(category);
CREATE INDEX idx_expenses_supplier_id ON expenses(supplier_id);
CREATE INDEX idx_expenses_ncf         ON expenses(ncf_proveedor) WHERE ncf_proveedor IS NOT NULL;

-- Permisos
INSERT INTO permissions (code, category, name, description) VALUES
  ('view.expenses',    'VIEW',     'Gastos',          'Acceso al módulo de gastos directos'),
  ('expenses.create',  'EXPENSES', 'Crear gastos',    'Permite registrar nuevos gastos'),
  ('expenses.edit',    'EXPENSES', 'Editar gastos',   'Permite editar gastos existentes'),
  ('expenses.delete',  'EXPENSES', 'Eliminar gastos', 'Permite eliminar gastos')
ON CONFLICT (code) DO NOTHING;

-- GESTOR: todo
INSERT INTO role_permissions (role, permission_id)
SELECT 'GESTOR', id FROM permissions
WHERE code IN ('view.expenses','expenses.create','expenses.edit','expenses.delete')
ON CONFLICT DO NOTHING;

-- ADMIN: todo
INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions
WHERE code IN ('view.expenses','expenses.create','expenses.edit','expenses.delete')
ON CONFLICT DO NOTHING;

-- MANAGER: ver + crear + editar
INSERT INTO role_permissions (role, permission_id)
SELECT 'MANAGER', id FROM permissions
WHERE code IN ('view.expenses','expenses.create','expenses.edit')
ON CONFLICT DO NOTHING;

-- CASHIER: sin acceso a gastos
