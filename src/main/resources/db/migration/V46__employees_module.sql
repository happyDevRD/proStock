-- F5: Módulo de Empleados / RRHH
-- Empleados, asistencia y comisiones por venta.

CREATE TABLE employees (
    id                   BIGSERIAL PRIMARY KEY,
    nombre_completo      VARCHAR(200) NOT NULL,
    cedula               VARCHAR(20)  UNIQUE,
    telefono             VARCHAR(20),
    email                VARCHAR(150),
    cargo                VARCHAR(100),
    departamento         VARCHAR(50)  NOT NULL DEFAULT 'OTRO',
    tipo_empleo          VARCHAR(30)  NOT NULL DEFAULT 'PERMANENTE',
    fecha_ingreso        DATE         NOT NULL,
    fecha_salida         DATE,
    salario_base         NUMERIC(18,2) NOT NULL DEFAULT 0,
    tipo_salario         VARCHAR(20)  NOT NULL DEFAULT 'MENSUAL',
    comision_porcentaje  NUMERIC(5,2) NOT NULL DEFAULT 0,
    num_tss              VARCHAR(30),
    banco                VARCHAR(100),
    cuenta_bancaria      VARCHAR(50),
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    foto_url             VARCHAR(500),
    notas                TEXT,
    created_by           VARCHAR(100),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_employees_status      ON employees(status);
CREATE INDEX idx_employees_departamento ON employees(departamento);
CREATE INDEX idx_employees_cedula      ON employees(cedula) WHERE cedula IS NOT NULL;

CREATE TABLE attendance_records (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT   NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    fecha       DATE     NOT NULL,
    hora_entrada TIME,
    hora_salida  TIME,
    tipo        VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    notas       TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attendance_employee_fecha ON attendance_records(employee_id, fecha);

-- Columna vendedor en ventas (nullable para no romper ventas existentes)
ALTER TABLE sales ADD COLUMN IF NOT EXISTS employee_id BIGINT REFERENCES employees(id) ON DELETE SET NULL;
CREATE INDEX idx_sales_employee_id ON sales(employee_id) WHERE employee_id IS NOT NULL;

-- Feature flag
INSERT INTO company_feature_config (feature_code, enabled, updated_at)
VALUES ('module.employees', true, NOW())
ON CONFLICT (feature_code) DO NOTHING;

-- Permisos
INSERT INTO permissions (code, category, name, description) VALUES
  ('view.employees',          'VIEW',      'Empleados',                   'Acceso al módulo de empleados'),
  ('employees.create',        'EMPLOYEES', 'Crear empleados',             'Registrar nuevos empleados'),
  ('employees.edit',          'EMPLOYEES', 'Editar empleados',            'Editar datos de empleados'),
  ('employees.delete',        'EMPLOYEES', 'Eliminar empleados',          'Eliminar empleados'),
  ('employees.view_salary',   'EMPLOYEES', 'Ver salarios',                'Ver información salarial'),
  ('employees.attendance',    'EMPLOYEES', 'Gestionar asistencia',        'Registrar y editar asistencia'),
  ('employees.commissions',   'EMPLOYEES', 'Ver comisiones',              'Ver reporte de comisiones por venta')
ON CONFLICT (code) DO NOTHING;

-- GESTOR: acceso completo
INSERT INTO role_permissions (role, permission_id)
SELECT 'GESTOR', id FROM permissions
WHERE code IN ('view.employees','employees.create','employees.edit','employees.delete',
               'employees.view_salary','employees.attendance','employees.commissions')
ON CONFLICT DO NOTHING;

-- ADMIN: todo excepto eliminar
INSERT INTO role_permissions (role, permission_id)
SELECT 'ADMIN', id FROM permissions
WHERE code IN ('view.employees','employees.create','employees.edit',
               'employees.view_salary','employees.attendance','employees.commissions')
ON CONFLICT DO NOTHING;

-- MANAGER: ver, asistencia, comisiones — sin salarios ni CRUD
INSERT INTO role_permissions (role, permission_id)
SELECT 'MANAGER', id FROM permissions
WHERE code IN ('view.employees','employees.attendance','employees.commissions')
ON CONFLICT DO NOTHING;
