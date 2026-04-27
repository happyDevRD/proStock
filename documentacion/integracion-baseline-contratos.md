# Baseline de Contratos de Integracion

## Objetivo

Consolidar el contrato tecnico entre frontend y backend para reducir retrabajo,
evitar supuestos incorrectos y priorizar gaps de API por dominio.

## Dominios cubiertos

- Productos
- Ventas y e-CF
- Clientes
- Inventario y movimientos
- Compras
- Configuracion de empresa
- Secuencias NCF
- Imagenes de productos

## Endpoints base disponibles

- `/api/products`
- `/api/sales`
- `/api/customers`
- `/api/stock-movements`
- `/api/purchase-orders`
- `/api/company-config`
- `/api/ncf-sequences`
- `/api/product-images`

## Reglas de negocio criticas (resumen)

- `completeSale` requiere venta `PENDING`, valida stock, calcula desglose fiscal,
  genera NCF si aplica y crea QR.
- `receivePurchaseOrder` cambia orden a `RECEIVED` y crea movimientos de entrada.
- Ajustes de stock y movimientos no permiten inconsistencias basicas (ejemplo:
  stock negativo en operaciones OUT).

## Gaps priorizados detectados

1. Falta de busqueda/filtros avanzados por texto y rango de fecha en endpoints
   principales.
2. Faltan endpoints de detalle optimizados para factura completa en frontend.
3. Exportacion (CSV/PDF) no estandarizada en reportes.
4. Seguridad actual basada en Basic in-memory no apta para produccion.
5. Manejo de errores funcional, pero no completamente homogeneo para UX de cliente.

## Backlog tecnico de integracion

### Backend

- Exponer filtros por fecha/estado/tipo para reportes y movimientos.
- Crear endpoint de detalle de venta para vista de factura.
- Agregar endpoint de exportacion de ventas.
- Endurecer seguridad por ambiente y restringir documentacion API en produccion.

### Frontend

- Modularizar vistas y capa de datos.
- Unificar estrategia de estado remoto (cache + invalidacion).
- Completar pantallas de inventario/reportes con filtros de backend.
- Eliminar placeholders no conectados en flujo fiscal.

## Entornos y configuracion

- Backend: centralizar configuracion por perfiles (`dev`, `staging`, `prod`).
- Frontend: documentar variables `VITE_API_*` y comportamiento de errores.
- Alinear CORS y origenes permitidos por ambiente.

## Criterio de cierre de baseline

Se considera cerrado cuando frontend y backend usan este documento como referencia
activa para la implementacion por fases y no existen contratos ambiguos en los
flujos de ventas, inventario y reportes.
