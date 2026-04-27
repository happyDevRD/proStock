# Checklist de Release - Integracion proStock

## 1) Validaciones tecnicas

- Backend compila: `./gradlew compileJava`
- Frontend lint: `npm run lint`
- Frontend build: `npm run build`
- Tests clave backend:
  - `SaleControllerExportTest`
  - `DgiiTaxUtilsTest`

## 2) Configuracion por ambiente

- Variables de BD definidas sin defaults inseguros en produccion.
- `app.security.allow-swagger=false` en perfil `prod`.
- `app.cors.allowed-origins` ajustado al dominio real.
- Credenciales bootstrap de seguridad definidas por entorno.

## 3) Flujos de negocio a validar manualmente

- POS:
  - crear cliente rapido
  - guardar borrador
  - cargar borrador y completar venta
- Factura:
  - muestra RNC/razon social de `company-config`
  - muestra NCF, montos y QR
- Inventario:
  - ajuste manual de stock
  - consulta por rango de movimientos
  - recepcion de orden de compra pendiente
- Reportes:
  - filtros por estado y fecha
  - exportacion CSV de ventas

## 4) Seguridad

- Confirmar autenticacion contra usuarios en BD (sin usuarios in-memory).
- Confirmar que endpoints protegidos requieren credenciales validas.
- Verificar subida/descarga de imagenes con validaciones basicas.

## 5) Cierre de release

- Ejecutar smoke test completo en staging.
- Aprobar checklist funcional/fiscal con el equipo.
- Crear tag de release y publicar notas de cambios.
