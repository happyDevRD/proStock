# API Company Config

## Endpoint base

`/api/company-config`

## GET /api/company-config

Obtiene la configuracion actual del emisor.

### Respuestas

- `200 OK`: configuracion encontrada.
- `404 Not Found`: no existe configuracion aun.

### Ejemplo 200

```json
{
  "id": 1,
  "rnc": "101234567",
  "razonSocial": "ProStock SRL",
  "nombreComercial": "ProStock",
  "direccion": "Av. 27 de Febrero #123",
  "municipioCodigo": "010100",
  "provinciaCodigo": "010100",
  "actividadEconomica": "Comercio al por mayor de articulos varios",
  "numeroTelefono": "8095551234",
  "correoElectronico": "facturacion@prostock.do"
}
```

## PUT /api/company-config

Crea o actualiza la configuracion del emisor (upsert).

### Request body

```json
{
  "rnc": "101234567",
  "razonSocial": "ProStock SRL",
  "nombreComercial": "ProStock",
  "direccion": "Av. 27 de Febrero #123",
  "municipioCodigo": "010100",
  "provinciaCodigo": "010100",
  "actividadEconomica": "Comercio al por mayor de articulos varios",
  "numeroTelefono": "8095551234",
  "correoElectronico": "facturacion@prostock.do"
}
```

### Respuestas

- `200 OK`: registro creado/actualizado.
- `400 Bad Request`: errores de validacion.
- `401 Unauthorized`: sin autenticacion.
- `403 Forbidden`: falla de CSRF o permisos insuficientes.

## Notas de seguridad

- Requiere Basic Auth.
- Para clientes browser, incluir cabeceras/cookies segun la configuracion de seguridad.
