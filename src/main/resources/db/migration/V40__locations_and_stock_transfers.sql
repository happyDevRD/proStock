-- V40: Multi-sucursal / almacén
-- products.stock sigue siendo el total. stock_locations almacena el desglose por ubicación.

CREATE TABLE locations (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    code        VARCHAR(30)   NOT NULL UNIQUE,
    type        VARCHAR(20)   NOT NULL DEFAULT 'BRANCH',  -- BRANCH | WAREHOUSE
    address     VARCHAR(255),
    phone       VARCHAR(30),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Sucursal principal (migra todo el stock existente aquí)
INSERT INTO locations (name, code, type, is_default)
VALUES ('Principal', 'MAIN', 'BRANCH', TRUE);

-- Stock por ubicación
CREATE TABLE stock_locations (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT  NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    location_id BIGINT  NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    quantity    INTEGER NOT NULL DEFAULT 0,
    UNIQUE (product_id, location_id)
);

-- Migrar stock existente a la ubicación por defecto
INSERT INTO stock_locations (product_id, location_id, quantity)
SELECT p.id, l.id, COALESCE(p.stock, 0)
FROM products p
CROSS JOIN locations l
WHERE l.is_default = TRUE;

-- Transferencias entre ubicaciones
CREATE TABLE stock_transfers (
    id               BIGSERIAL PRIMARY KEY,
    from_location_id BIGINT REFERENCES locations(id),
    to_location_id   BIGINT NOT NULL REFERENCES locations(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | COMPLETED | CANCELED
    notes            TEXT,
    created_by_id    BIGINT REFERENCES users(id),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP
);

CREATE TABLE stock_transfer_items (
    id           BIGSERIAL PRIMARY KEY,
    transfer_id  BIGINT  NOT NULL REFERENCES stock_transfers(id) ON DELETE CASCADE,
    product_id   BIGINT  NOT NULL REFERENCES products(id),
    quantity     INTEGER NOT NULL CHECK (quantity > 0)
);

-- location_id en ventas (null = sin datos de ubicación / instancias sin el módulo)
ALTER TABLE sales ADD COLUMN location_id BIGINT REFERENCES locations(id);

-- Apuntar ventas existentes a la sucursal principal
UPDATE sales SET location_id = (SELECT id FROM locations WHERE is_default = TRUE);

-- Permisos del módulo
INSERT INTO permissions (code, description) VALUES
    ('locations.view',     'Ver sucursales y almacenes'),
    ('locations.edit',     'Crear y editar sucursales'),
    ('locations.transfer', 'Realizar transferencias de stock entre ubicaciones')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_code)
VALUES
    ('ADMIN',   'locations.view'),
    ('ADMIN',   'locations.edit'),
    ('ADMIN',   'locations.transfer'),
    ('GESTOR',  'locations.view'),
    ('GESTOR',  'locations.edit'),
    ('GESTOR',  'locations.transfer')
ON CONFLICT DO NOTHING;
