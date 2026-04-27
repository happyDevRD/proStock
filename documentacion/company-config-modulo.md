# Modulo Company Config (Emisor)

## Objetivo

Persistir en el backend una configuracion unica de la empresa emisora para que futuras facturas cuenten con la informacion fiscal minima requerida.

## Diseno implementado

- Entidad: `CompanyConfig`
- Repositorio: `CompanyConfigRepository`
- Servicio: `CompanyConfigService` / `CompanyConfigServiceImpl`
- Controlador: `CompanyConfigController`
- Migracion: `V4__create_company_config_table.sql`

## Comportamiento funcional

- El modulo funciona como **single record**:
  - Si no existe configuracion previa, `PUT /api/company-config` crea el registro.
  - Si ya existe, `PUT /api/company-config` actualiza el primer registro existente.
- `GET /api/company-config` devuelve:
  - `200 OK` con datos si existe configuracion.
  - `404 Not Found` si no existe.

## Campos soportados

- `rnc`: 9 u 11 digitos numericos.
- `razonSocial`: nombre legal.
- `nombreComercial`: opcional.
- `direccion`: direccion comercial/fiscal.
- `municipioCodigo`: codigo numerico de 6 digitos.
- `provinciaCodigo`: codigo numerico de 6 digitos.
- `actividadEconomica`: descripcion de la actividad.
- `numeroTelefono`: telefono de contacto.
- `correoElectronico`: correo valido.

## Consideraciones

- El endpoint esta bajo seguridad de la API (Basic Auth configurado globalmente).
- En ambiente de pruebas de controlador se requiere usuario mock y CSRF para `PUT`.
