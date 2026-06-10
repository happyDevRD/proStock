-- V30: Sistema de permisos granular por rol/usuario (Fase 1 - modularización)
-- Reemplaza los checks hardcodeados de UserRole por un catálogo de permisos
-- y una matriz rol -> permisos editable. GESTOR mantiene bypass total por
-- código (PermissionService), esta tabla solo existe para que la matriz UI
-- se vea íntegra para ese rol.

CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    category VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(20) NOT NULL,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    UNIQUE (role, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role);

-- Preparado para overrides por usuario (sin UI en Fase 1, pero
-- PermissionService.getEffectivePermissions ya lo aplica desde el día 1
-- para evitar otra migración disruptiva después)
CREATE TABLE IF NOT EXISTS user_permission_overrides (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted BOOLEAN NOT NULL,
    UNIQUE (user_id, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_user_permission_overrides_user ON user_permission_overrides(user_id);

-- Catálogo de permisos (replica el comportamiento ACTUAL de los 5 roles,
-- derivado de lib/permissions.ts, SecurityConfig.java y AccAccountController)
INSERT INTO permissions (code, category, name, description) VALUES
  ('view.dashboard', 'VIEW', 'Ver dashboard', 'Acceso a la vista de panel principal'),
  ('view.setup', 'VIEW', 'Asistente inicial', 'Acceso al asistente de configuración inicial'),
  ('view.pos', 'VIEW', 'Punto de venta', 'Acceso a la vista de ventas (POS)'),
  ('view.invoice', 'VIEW', 'Facturas', 'Acceso al historial y detalle de facturas'),
  ('view.inventory', 'VIEW', 'Inventario', 'Acceso a la vista de inventario'),
  ('view.suppliers', 'VIEW', 'Suplidores', 'Acceso a la vista de proveedores'),
  ('view.customers', 'VIEW', 'Clientes', 'Acceso al directorio de clientes'),
  ('view.users', 'VIEW', 'Usuarios', 'Acceso a la gestión de usuarios'),
  ('view.reports', 'VIEW', 'Reportes', 'Acceso a reportes y análisis'),
  ('view.ar', 'VIEW', 'Cuentas por cobrar', 'Acceso a cuentas por cobrar'),
  ('view.ap', 'VIEW', 'Cuentas por pagar', 'Acceso a cuentas por pagar'),
  ('view.settings', 'VIEW', 'Ajustes', 'Acceso a la configuración del sistema'),
  ('view.accounting', 'VIEW', 'Contabilidad', 'Acceso al módulo contable'),
  ('view.service_orders', 'VIEW', 'Órdenes de servicio', 'Acceso al seguimiento de órdenes de servicio'),
  ('inventory.module.articles', 'INVENTORY', 'Artículos', 'Submódulo de artículos en inventario'),
  ('inventory.module.purchases', 'INVENTORY', 'Compras', 'Submódulo de compras en inventario'),
  ('inventory.module.adjustments', 'INVENTORY', 'Ajustes', 'Submódulo de ajustes de stock'),
  ('inventory.delete_item', 'INVENTORY', 'Eliminar artículos', 'Permite eliminar artículos del inventario'),
  ('inventory.import_csv', 'INVENTORY', 'Importar CSV', 'Permite importar productos vía CSV'),
  ('inventory.bulk_promo_price', 'INVENTORY', 'Promociones masivas', 'Permite aplicar precios promocionales masivos'),
  ('inventory.view_costs', 'INVENTORY', 'Ver costos', 'Permite ver costos de productos'),
  ('suppliers.edit', 'SUPPLIERS', 'Editar proveedores', 'Permite crear/editar proveedores'),
  ('accounting.module.view', 'ACCOUNTING', 'Ver contabilidad', 'Acceso a submódulos de contabilidad (plan de cuentas, diario, mayor, balanzas, estados)'),
  ('accounting.accounts.create', 'ACCOUNTING', 'Crear cuentas contables', 'Permite crear cuentas en el plan de cuentas'),
  ('users.manage', 'USERS', 'Gestionar usuarios', 'Permite crear, editar y eliminar usuarios (excepto GESTOR)'),
  ('users.manage_gestor', 'USERS', 'Gestionar usuarios GESTOR', 'Permite crear, editar y eliminar usuarios con rol GESTOR'),
  ('audit.view', 'SETTINGS', 'Ver auditoría', 'Acceso al registro de auditoría'),
  ('settings.manage_company_config', 'SETTINGS', 'Configurar empresa', 'Permite editar datos de la empresa, NCF, carga inicial'),
  ('settings.manage_features', 'SETTINGS', 'Gestionar features', 'Permite activar/desactivar funcionalidades del sistema'),
  ('settings.manage_permissions', 'SETTINGS', 'Gestionar permisos', 'Permite editar la matriz de permisos por rol')
ON CONFLICT (code) DO NOTHING;

-- ADMIN: todo excepto gestionar usuarios GESTOR
INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions
WHERE code NOT IN ('users.manage_gestor')
ON CONFLICT DO NOTHING;

-- MANAGER
INSERT INTO role_permissions (role, permission_id)
SELECT 'MANAGER', id FROM permissions WHERE code IN (
  'view.dashboard','view.pos','view.invoice','view.inventory','view.suppliers',
  'view.customers','view.reports','view.ar','view.ap','view.settings','view.accounting',
  'view.service_orders',
  'inventory.module.articles','inventory.module.purchases','inventory.module.adjustments',
  'suppliers.edit','accounting.module.view','accounting.accounts.create'
) ON CONFLICT DO NOTHING;

-- CASHIER
INSERT INTO role_permissions (role, permission_id)
SELECT 'CASHIER', id FROM permissions WHERE code IN (
  'view.dashboard','view.pos','view.invoice','view.customers','view.ar','view.ap',
  'view.service_orders'
) ON CONFLICT DO NOTHING;

-- USER
INSERT INTO role_permissions (role, permission_id)
SELECT 'USER', id FROM permissions WHERE code IN ('view.dashboard')
ON CONFLICT DO NOTHING;

-- GESTOR: se siembra completo solo para que la matriz UI se vea íntegra;
-- el bypass real de superusuario se hace por código (no depende de esta tabla)
INSERT INTO role_permissions (role, permission_id)
SELECT 'GESTOR', id FROM permissions
ON CONFLICT DO NOTHING;
