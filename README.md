# ProStock Backend

Backend REST para gestion de inventario, compras y ventas con Spring Boot.

## Que resuelve

- Catalogo de productos con categoria, proveedor e imagenes.
- Control de stock minimo y consulta de productos bajo minimo.
- Flujo de ventas (pendiente/completada) con descuento de stock.
- Flujo de ordenes de compra (pendiente/recibida) con aumento de stock.
- Registro de movimientos de stock para trazabilidad.

## Stack

- Java 17
- Spring Boot 3.4
- Spring Web, Spring Data JPA, Spring Security, Actuator
- PostgreSQL
- Flyway
- MapStruct + Lombok
- OpenAPI/Swagger

## Arranque local

### 1) Base de datos

Debes tener PostgreSQL accesible. Por defecto:

- host: `localhost`
- puerto: `5432`
- base: `prestockdb`
- usuario: `postgres`
- clave: `admin`

Tambien puedes usar variables de entorno:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

### 2) Ejecutar app

```bash
./gradlew bootRun
```

En Windows:

```powershell
.\gradlew.bat bootRun
```

La API levanta en `http://localhost:8080`.

## Seguridad

Se usa Basic Auth en memoria.

- `admin / adminpassword`
- `user / password`

Rutas publicas:

- `GET /`
- `GET /actuator/health`
- `/swagger-ui/**`
- `/v3/api-docs/**`

CORS se configura globalmente con `app.cors.allowed-origins` (lista separada por comas).

## Endpoints principales

### Home y estado

- `GET /` -> texto simple de estado de API.
- `GET /actuator/health` -> health check.

### Productos

- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`
- `GET /api/products/below-min-stock`
- `GET /api/products/below-min-stock/paginated`
- `POST /api/products/{id}/stock-adjustments`

Ejemplo de ajuste de stock:

```json
{
  "quantityChange": -2,
  "type": "OUT",
  "reason": "Ajuste manual por merma",
  "batchNumber": "LOT-2026-04",
  "expirationDate": "2026-12-31T00:00:00",
  "unitCost": 12.50,
  "sourceLocationId": null,
  "destinationLocationId": null
}
```

### Ventas

- `GET /api/sales`
- `GET /api/sales/paginated`
- `GET /api/sales/{id}`
- `POST /api/sales`
- `PUT /api/sales/{id}`
- `DELETE /api/sales/{id}`
- `PUT /api/sales/{id}/complete`

### Movimientos de stock

- `GET /api/stock-movements` (paginado)
- `GET /api/stock-movements/{id}`
- `GET /api/stock-movements/product/{productId}`
- `GET /api/stock-movements/type/{type}`
- `GET /api/stock-movements/sale/{saleId}`
- `GET /api/stock-movements/purchase-order/{purchaseOrderId}`

### Ordenes de compra

- `GET /api/purchase-orders`
- `GET /api/purchase-orders/paginated`
- `GET /api/purchase-orders/{id}`
- `POST /api/purchase-orders`
- `PUT /api/purchase-orders/{id}`
- `DELETE /api/purchase-orders/{id}`
- `PUT /api/purchase-orders/{id}/receive`

### Catalogos auxiliares

- `GET|POST|PUT|DELETE /api/categories`
- `GET|POST|PUT|DELETE /api/customers`
- `GET|POST|PUT|DELETE /api/suppliers`
- `GET|POST|PUT|DELETE /api/product-images`

## Swagger

- UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

## Documentacion

- Ver `documentacion/README.md` para la documentacion del modulo de configuracion de empresa (emisor) y su integracion.

## Notas tecnicas relevantes

- Al completar una venta o recibir una orden de compra se registra `StockMovement`, evitando desalineacion entre stock actual e historial.
- El ajuste de stock manual ya esta expuesto por API en productos.
- Si el usuario autenticado no existe en tabla `users`, el movimiento no falla: se registra sin `user`.
