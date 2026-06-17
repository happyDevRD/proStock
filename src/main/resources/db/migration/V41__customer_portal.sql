-- V41: Portal del cliente final + módulo de IA
-- Agrega credenciales de acceso al portal para clientes del negocio

ALTER TABLE customers
    ADD COLUMN portal_enabled    BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN portal_password   VARCHAR(255),
    ADD COLUMN portal_last_login TIMESTAMP;

-- Permisos de portal e IA
INSERT INTO permissions (code, category, name, description) VALUES
    ('portal.manage', 'PORTAL', 'Gestionar portal', 'Gestionar acceso de clientes al portal'),
    ('view.ai',       'AI',     'Ver IA',           'Acceder al módulo de IA (asistente y anomalías)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT role_name, p.id
FROM (VALUES
    ('GESTOR',  'portal.manage'),
    ('ADMIN',   'portal.manage'),
    ('GESTOR',  'view.ai'),
    ('ADMIN',   'view.ai'),
    ('MANAGER', 'view.ai')
) AS role_map(role_name, permission_code)
JOIN permissions p ON p.code = role_map.permission_code
ON CONFLICT DO NOTHING;
