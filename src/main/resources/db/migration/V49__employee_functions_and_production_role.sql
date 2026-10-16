-- Funciones de producción de los empleados, vínculo empleado ↔ usuario y rol PRODUCCION
-- (fotógrafos y editores con acceso solo a "Mis trabajos"). Ver documentacion/carpetas-ordenes.md.

ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS es_fotografo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS es_editor BOOLEAN NOT NULL DEFAULT FALSE,
    -- Usuario con el que el empleado entra al sistema (uno a uno, opcional)
    ADD COLUMN IF NOT EXISTS user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE SET NULL;

INSERT INTO permissions (code, category, name, description) VALUES
  ('view.my_work', 'VIEW', 'Mis trabajos', 'Órdenes asignadas al usuario como fotógrafo o editor, y sus avances')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT r.role, p.id
FROM permissions p
CROSS JOIN (VALUES ('GESTOR'), ('ADMIN'), ('MANAGER'), ('PRODUCCION')) AS r(role)
WHERE p.code = 'view.my_work'
ON CONFLICT DO NOTHING;
