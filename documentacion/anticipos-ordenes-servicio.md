# Anticipos en órdenes de servicio

Diseño acordado (2026-10-14) para tratar los anticipos de las órdenes de servicio (fotografía y demás tipos) como cobros reales con comprobante fiscal.

## Problema anterior

El anticipo era solo un número en la orden (`service_orders.deposit_amount`):

- No registraba un pago: no entraba a caja ni al cuadre.
- No emitía NCF ni aparecía en el 607; no generaba asiento contable.
- "Generar factura" cargaba todas las líneas al POS por el total; si el cajero no restaba el anticipo, se cobraba dos veces.
- El saldo mezclaba montos sin ITBIS (estimado) con montos cobrados con ITBIS.
- Al cancelar no había forma de devolver o retener el anticipo.

## Criterio fiscal

En servicios, el ITBIS nace con la factura **o con el cobro total o parcial, lo que ocurra primero** (Ley 11-92). Por eso cada anticipo emite su propio NCF/e-CF con ITBIS incluido, y la factura final solo cobra el saldo, sin volver a declarar lo ya facturado. (Validar con el contador.)

## Decisiones

| Tema | Decisión |
|---|---|
| Monto del anticipo | Libre en cada cobro; puede haber varios anticipos de montos distintos por orden. |
| Corrección de un anticipo | No se edita una factura emitida: se anula con nota de crédito (E34) y se registra de nuevo. |
| Factura final | **Opción B**: una factura por el **saldo** (`Total − anticipos`), con el detalle de lo entregado como nota. |
| Montos de la orden | Se muestran **con ITBIS** (lo que paga el cliente). |
| Stock | Los bienes de la orden (álbum, canvas, USB) se descuentan al completar la orden. |
| Cancelación | Se elige: **retener** los anticipos (quedan como ingreso) o **devolverlos** (nota de crédito por cada anticipo + salida de caja). |
| Entrega | No se puede completar una orden con saldo pendiente. |

## Flujo

```
 CLIENTE              ORDEN DE SERVICIO                    FACTURACIÓN / CAJA                  CONTABILIDAD / DGII
 ───────              ─────────────────                    ──────────────────                  ───────────────────
    │
    │  solicita sesión
    ├──────────────────► [1] CREAR ORDEN
    │                     · servicio + extras (líneas)
    │                     · Total orden = Σ líneas (con ITBIS)
    │                     · Anticipos = 0 · Saldo = Total
    │                          │
    │  paga anticipo           │ "Cobrar anticipo"
    │  (monto libre)           │  monto: RD$ ______  (editable)
    ├──────────────────────────┼──────────────────► [2] FACTURA DE ANTICIPO
    │                          │                     · línea "Anticipo – OS-0013"
    │                          │                     · NCF E32 / E31 (si RNC)
    │                          │                     · ITBIS incluido en el monto
    │                          │                     · pago en caja (efectivo/tarjeta/transf.)
    │                          │                     · COMPLETED + vinculada a la orden ──────► asiento: Dr Caja/Banco
    │  ◄── recibo con NCF ─────┼─────────────────────┘                                          Cr Ventas
    │                          │                                                                Cr 2.1.2 ITBIS
    │                          ▼                                                                607: incluido ✓
    │                     Anticipos = Σ facturas de anticipo
    │                     Saldo = Total − Anticipos
    │                          │
    │  otro abono              │  (se repite [2] cuantas veces haga falta,
    │  (otro monto)  ──────────┼───► cada abono con su propio NCF)
    │                          │
    │                          ▼
    │                     [3] FLUJO DE ETAPAS (sin cambios)
    │                     Agendado → Sesión → Selección → Edición → Listo
    │                          │
    │  agrega extras           │  + líneas (fotos extra, canvas…)
    ├─────────────────────────►│  Total sube → Saldo se recalcula solo
    │                          │
    │  retira su trabajo       ▼
    ├─────────────────► [4] "Facturar saldo"
    │                     · Saldo > 0 ─────────────────────► [5] FACTURA FINAL (SALDO)
    │                                                         · línea "Saldo – OS-0013: <título>"
    │                                                         · detalle de lo entregado como nota
    │                                                         · monto = Saldo (con ITBIS)
    │                                                         · NCF + pago en caja ─────────────► asiento + 607 ✓
    │  ◄── factura final ────────────────────────────────────┘
    │                          │
    │                          ▼
    │                     [6] COMPLETAR ORDEN → Entregado
    │                     · descuenta stock de bienes (álbum, canvas, USB)
    │                     · Σ facturas (anticipos + saldo) = Total orden ✓
```

### Casos especiales

```
    │  anticipo mal digitado      → Nota de crédito E34 sobre ese anticipo → se registra de nuevo
    │
    │  quitan líneas y            → Saldo negativo → aviso: "Anticipos superan el total"
    │  Anticipos > Total             → NC por el excedente + devolución en caja
    │
    │  cancela la orden ──► ¿qué pasa con los anticipos?
    │                        ├─ Retener (política) → quedan como ingreso, sin documentos nuevos
    │                        └─ Devolver           → NC E34 por cada anticipo + salida de caja
    │
    │  Saldo = 0 al entregar      → no se emite factura final, solo se completa la orden
```

## Cálculo del saldo

```
Total orden   = Σ (cantidad × precio base × (1 + tasa ITBIS))   de las líneas de la orden
Cobrado       = Σ facturas vinculadas COMPLETED (anticipos, saldo y facturas del flujo anterior)
              − Σ notas de crédito de esas facturas
              + anticipo anterior sin factura (dato heredado)
Saldo         = Total orden − Cobrado
```

## Datos heredados

Los valores de `deposit_amount` registrados antes de este cambio no tienen factura. La migración los mueve a `legacy_deposit_amount`: cuentan para el saldo (el cliente sí pagó) y la orden muestra un aviso de "anticipo anterior sin factura".

## Implementación

### Backend

| Pieza | Detalle |
|---|---|
| Migración `V47__service_order_deposits.sql` | `sales.sale_kind` (`NORMAL` / `DEPOSIT` / `BALANCE`); `service_orders.legacy_deposit_amount` (anticipos anteriores sin factura). |
| `POST /api/service-orders/{id}/deposits` | Cobra un anticipo de monto libre: `{amount, paymentMethod, tipoComprobante?, notes?}`. No puede superar el saldo. |
| `POST /api/service-orders/{id}/deposits/{saleId}/void` | Anula un anticipo con nota de crédito E34: `{reason?}`. |
| `POST /api/service-orders/{id}/balance-invoice` | Factura y cobra el saldo completo: `{paymentMethod, tipoComprobante?}`. |
| `POST /api/service-orders/{id}/cancel` | Acepta `refundDeposits: true` para devolver los anticipos con nota de crédito. |
| `complete` / avanzar a la etapa final | Rechaza la entrega con saldo pendiente; la etapa final siempre pasa por `complete()` (stock). |
| `SaleService.createServiceOrderCharge` | Venta de una línea con NCF y pago en una transacción. El total es exactamente el monto cobrado: el ITBIS absorbe el céntimo de redondeo. |
| Productos internos | `SYS-ANTICIPO-OS` y `SYS-SALDO-OS` (servicio, ITBIS 18 %, no se venden en el POS). Se crean solos la primera vez. |
| `ServiceOrderDto` | `totalAmount`, `billedAmount`, `balance`, `legacyDepositAmount`; `linkedSales[].kind`. |

### Frontend

- `ServiceOrderPayments.tsx`: tarjeta "Cobros" (total, cobrado, saldo, anticipos con anular, cobrar anticipo, facturar saldo).
- Detalle de la orden: se quitó "Generar factura / Facturar pendientes" (ruta orden → POS); entrega bloqueada con saldo; cancelación con retener o devolver.
- Formulario de nueva orden: "Anticipo a cobrar ahora" + forma de pago (emite la factura de anticipo al crear).
- Tablero: cada tarjeta muestra el saldo real.
- e2e `service-order-billing.spec.ts`: anticipo + saldo → dos facturas E32 y saldo 0.

### Contabilidad

- El asiento de cada venta usa el **total original** y separa los ingresos: bienes → 4.1.1 Ventas de Bienes, servicios → 4.1.2 Ventas de Servicios (proporcional a la base de cada línea; los servicios absorben el redondeo).
- Cada **nota de crédito** tiene su asiento de reverso (`AUTO_CREDIT_NOTE`): Dr ventas (bienes/servicios) + Dr ITBIS, Cr caja/banco. Así un anticipo anulado o devuelto queda revertido en contabilidad.
- Ventas sincronizadas antes de este cambio con su nota ya descontada no se revierten dos veces (se detecta comparando el asiento con el total original).
- El aviso de sincronización muestra también las notas de crédito pendientes.

### Portal del cliente

- Listado: total con ITBIS y saldo de cada orden.
- Detalle: total, pagado, saldo y cada factura de la orden (anticipo / saldo) con su NCF y fecha.

### Montos con ITBIS en la orden

- Las líneas, el buscador de productos y el total de la orden se muestran con ITBIS; el precio manual se digita con ITBIS y se guarda como base.
- El anticipo heredado (sin factura) solo cubre lo que las facturas no cubren: nunca genera saldo negativo en órdenes del flujo anterior facturadas completas por POS.
