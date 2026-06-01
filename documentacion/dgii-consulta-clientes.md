# Consulta RNC / Cédula (DGII vía MegaPlus)

ProStock consulta datos de contribuyentes en la DGII a través de la [MegaPlus API](https://rnc.megaplus.com.do/apidocs/), con proxy en el backend para evitar CORS y centralizar errores.

## Endpoint interno

`GET /api/dgii/consulta?rnc={9 u 11 dígitos}`

Requiere sesión autenticada (misma cookie JWT que el resto del API).

### Respuesta exitosa (ejemplo)

```json
{
  "rncCedula": "131996035",
  "tipoIdentificacion": "RNC",
  "nombreRazonSocial": "AGROPECUARIA DELIA & MILO AGRODEMI SRL",
  "nombreComercial": "AGROPECUARIA DELIA & MILO AGRODEMI",
  "estado": "ACTIVO",
  "regimenPagos": "NORMAL",
  "actividadEconomica": "...",
  "facturadorElectronico": "SI",
  "suggestedFirstName": "AGROPECUARIA DELIA & MILO AGRODEMI",
  "suggestedLastName": "."
}
```

### Códigos de error

| code | HTTP | Significado |
|------|------|-------------|
| `DGII_INVALID_RNC` | 400 | Formato inválido (no 9 ni 11 dígitos) |
| `DGII_NOT_FOUND` | 404 | No inscrito como contribuyente |
| `DGII_UNAVAILABLE` | 502 | MegaPlus no alcanzable |
| `DGII_DISABLED` | 503 | Integración desactivada |

## Configuración

En `application.properties` o variables de entorno:

| Propiedad | Env | Default |
|-----------|-----|---------|
| `app.dgii.megaplus.enabled` | `DGII_MEGAPLUS_ENABLED` | `true` |
| `app.dgii.megaplus.base-url` | `DGII_MEGAPLUS_BASE_URL` | `https://rnc.megaplus.com.do` |
| `app.dgii.megaplus.connect-timeout-ms` | `DGII_MEGAPLUS_CONNECT_TIMEOUT_MS` | `10000` |
| `app.dgii.megaplus.read-timeout-ms` | `DGII_MEGAPLUS_READ_TIMEOUT_MS` | `30000` |

## Frontend

En el POS, botón **+** del ticket abre **Cliente en factura**:

- **Buscar / DGII**: clientes guardados, consulta por RNC/cédula o búsqueda por nombre en la DGII, ficha con estado/régimen/actividad.
- **Registrar nuevo**: formulario con razón social unificada para empresas (RNC); email obligatorio (la DGII no lo publica).

Endpoints usados: `GET /api/dgii/consulta`, `GET /api/dgii/consulta/nombres`.

## Referencias externas

- Documentación MegaPlus: https://rnc.megaplus.com.do/apidocs/
- Repositorio de referencia: https://github.com/nsmdeveloper/api_consulta_rnc_cedula_dgii
