# Validaciones del modulo Company Config

Este documento describe las validaciones de entrada que se aplican actualmente en backend para el emisor.

## Reglas por campo

- `rnc`
  - Requerido (`@NotBlank`)
  - Solo digitos, longitud 9 o 11 (`^\\d{9}(\\d{2})?$`)
  - Unico en base de datos
- `razonSocial`
  - Requerido
  - Maximo 255 caracteres
- `nombreComercial`
  - Opcional
  - Maximo 255 caracteres
- `direccion`
  - Requerido
  - Maximo 255 caracteres
- `municipioCodigo`
  - Requerido
  - Exactamente 6 digitos numericos
- `provinciaCodigo`
  - Requerido
  - Exactamente 6 digitos numericos
- `actividadEconomica`
  - Requerido
  - Maximo 255 caracteres
- `numeroTelefono`
  - Requerido
  - Maximo 20 caracteres
- `correoElectronico`
  - Requerido
  - Debe tener formato email valido
  - Maximo 255 caracteres

## Validaciones en BD (Flyway V4)

- `chk_company_config_rnc_length`: largo de RNC en 9 o 11.
- `chk_company_config_municipio_codigo_length`: largo 6.
- `chk_company_config_provincia_codigo_length`: largo 6.

## Ejemplo de payload invalido

```json
{
  "rnc": "12345",
  "razonSocial": "",
  "nombreComercial": "ProStock",
  "direccion": "Av. 27 de Febrero #123",
  "municipioCodigo": "101",
  "provinciaCodigo": "ABCDEF",
  "actividadEconomica": "",
  "numeroTelefono": "",
  "correoElectronico": "correo-invalido"
}
```

## Respuesta de error esperada

El formato lo construye `GlobalExceptionHandler` bajo el codigo `VALIDATION_FAILED`, con mapa `errors` por campo.
