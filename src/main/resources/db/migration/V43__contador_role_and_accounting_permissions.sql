-- V43: Rol CONTADOR + permisos granulares del módulo contable
-- El CONTADOR tiene acceso completo a contabilidad, reportes, gastos, CxC y CxP
-- pero NO accede a POS, inventario ni gestión de usuarios.

-- ── Nuevos permisos contables granulares ──────────────────────────────────
INSERT INTO permissions (code, category, name, description) VALUES
  ('accounting.journal.create',     'ACCOUNTING', 'Crear asientos',         'Permite crear asientos contables manuales'),
  ('accounting.journal.edit',       'ACCOUNTING', 'Editar asientos',        'Permite editar asientos en estado borrador'),
  ('accounting.journal.post',       'ACCOUNTING', 'Contabilizar asientos',  'Permite contabilizar (postear) asientos borrador'),
  ('accounting.journal.delete',     'ACCOUNTING', 'Eliminar asientos',      'Permite eliminar asientos borrador'),
  ('accounting.journal.reverse',    'ACCOUNTING', 'Revertir asientos',      'Permite crear el asiento de reversión de uno ya contabilizado'),
  ('accounting.accounts.edit',      'ACCOUNTING', 'Editar cuentas',         'Permite editar el catálogo de cuentas contables'),
  ('accounting.accounts.deactivate','ACCOUNTING', 'Desactivar cuentas',     'Permite desactivar cuentas contables'),
  ('accounting.reports.export',     'ACCOUNTING', 'Exportar reportes',      'Permite descargar reportes contables en Excel/PDF')
ON CONFLICT (code) DO NOTHING;

-- ── GESTOR: hereda todo por bypass de código; no necesita filas, pero
--    añadimos los nuevos permisos a ADMIN también (tiene acceso completo) ──
INSERT INTO role_permissions (role, permission_id)
SELECT 'GESTOR', id FROM permissions WHERE code IN (
  'accounting.journal.create','accounting.journal.edit','accounting.journal.post',
  'accounting.journal.delete','accounting.journal.reverse',
  'accounting.accounts.edit','accounting.accounts.deactivate',
  'accounting.reports.export'
) ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions WHERE code IN (
  'accounting.journal.create','accounting.journal.edit','accounting.journal.post',
  'accounting.journal.delete','accounting.journal.reverse',
  'accounting.accounts.edit','accounting.accounts.deactivate',
  'accounting.reports.export'
) ON CONFLICT DO NOTHING;

-- MANAGER: puede crear y contabilizar, pero no revertir ni desactivar cuentas
INSERT INTO role_permissions (role, permission_id)
SELECT 'MANAGER', id FROM permissions WHERE code IN (
  'accounting.journal.create','accounting.journal.edit','accounting.journal.post',
  'accounting.reports.export'
) ON CONFLICT DO NOTHING;

-- ── Rol CONTADOR ──────────────────────────────────────────────────────────
-- Vistas: contabilidad, reportes, CxC, CxP, gastos (lectura de facturas y compras para contexto)
INSERT INTO role_permissions (role, permission_id)
SELECT 'CONTADOR', id FROM permissions WHERE code IN (
  -- Vistas
  'view.dashboard',
  'view.accounting',
  'view.reports',
  'view.ar',
  'view.ap',
  'view.expenses',
  'view.invoice',
  -- Contabilidad completa
  'accounting.module.view',
  'accounting.accounts.create',
  'accounting.accounts.edit',
  'accounting.accounts.deactivate',
  'accounting.journal.create',
  'accounting.journal.edit',
  'accounting.journal.post',
  'accounting.journal.delete',
  'accounting.journal.reverse',
  'accounting.reports.export',
  -- DGII 606/607/608 (dentro de Reportes)
  'view.reports'
) ON CONFLICT DO NOTHING;
