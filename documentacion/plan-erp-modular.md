# Plan de ejecución — ProStock ERP Modular

> **Documento vivo.** Se actualiza al final de cada sesión de trabajo. Si el
> contexto se corta o empezamos una sesión nueva, lee primero la sección
> **"2. Estado actual / próximo paso"** para retomar exactamente donde quedamos.

## 1. Visión

(Planteada por el usuario, 2026-06-10; ampliada 2026-06-11)

ProStock deja de ser un simple sistema de ventas/POS y pasa a ser un **ERP
modular estilo Odoo, con mejoras y funcionalidades específicas propias**, con
la ambición de convertirse en un **referente comercial** — primero en
República Dominicana, luego en la región. Objetivos concretos:

- Reorganizar mejor el contenido y el menú.
- Facilitar y ampliar funcionalidades existentes.
- Interfaz más amigable.
- Dashboard muchísimo más completo.
- **Modularizar la aplicación** — habilitar/deshabilitar funciones por
  usuario/instancia, con personalización (empleados, WhatsApp, redes
  sociales, y más módulos a futuro).
- **(Nuevo 2026-06-11) Vanguardia comercial:** cumplimiento fiscal RD
  completo (606/607, e-CF), calidad de producto vendible (tests, seguridad,
  operación multi-cliente), e innovación real (IA aplicada, pagos
  integrados, portal de cliente).

## 2. Estado actual / próximo paso

- **Última actualización:** 2026-06-11
- **Fase 1 (fundación de modularización): ✅** `proStock@705581b`,
  `proStockFront@40c6818`
- **Fase 2 (Centro de Módulos): ✅** `proStockFront@d356601`
- **Fase 3 (Menú y navegación): ✅** `proStockFront@8e5ac84`,
  `proStock@0098db3` (docs)
- **Fase 4.1 (Dashboard — reorganización y personalización): ✅ y pusheada**
  (commiteada vía Cursor el 2026-06-11 en la serie `ffd2336`…`622ffe6` de
  `proStockFront`, rama `continue-screens`).
- **Trabajo grande adicional del 2026-06-11 (vía Cursor), ✅ pusheado:**
  - Backend `proStock@f881240`: **notas de crédito (NCF tipo 34)** con
    migraciones V31/V32, **recepción parcial de órdenes de compra**, filtro
    `overdueOnly` en facturas, paginación filtrada de movimientos de stock.
  - Backend `proStock@54534cc`: consolidación de **órdenes de servicio** —
    paginación, KPIs, reportes por período, descuento de stock configurable
    al completar (V33), facturación parcial (cantidades facturadas vs
    pendientes), endpoints protegidos por feature flag + permisos.
  - Frontend `proStockFront@54f466b`: historial de movimientos, import CSV
    con preview, recepción parcial OC, CxC/CxP con antigüedad, recordatorios
    email/WhatsApp, PDF estado de cuenta, notas de crédito, reportes de
    margen/rotación, mejoras POS/facturas.
  - Frontend `proStockFront@71cddb7`: integración de órdenes de servicio en
    dashboard/clientes/facturas/reportes/POS, Kanban paginado con deep
    links, facturación parcial en UI, sincronización offline más tolerante.
  - ⚠️ **Nota de ramas:** `proStockFront` trabaja en `continue-screens`
    (= `main` + `71cddb7`). Pendiente merge/PR a `main` cuando se valide.
- **Esta sesión (2026-06-11):** reescritura de este plan (análisis crítico +
  hoja de ruta en 3 carriles) y **Fase C1 casi completa**: implementados los
  reportes DGII **606, 607 y 608** (backend + UI en Reportes + tests +
  migración V34). Quedan las validaciones de cierre de mes y —crítico— la
  **validación de los TXT con la herramienta oficial de la DGII**.
- **Próximo paso sugerido:** validar TXT con el pre-validador DGII (manual,
  usuario), y arrancar **Fase Q1** (suite e2e + seeds de prueba) o las
  validaciones de cierre de mes de C1.
- **Fase C2 (e-CF) en pausa:** la certificación requiere tener el software
  en venta primero; se retoma cuando el usuario reciba el visto bueno.
- **Issues conocidos, no bloqueantes:**
  - `AccessDeniedException` devuelve HTTP 401 en vez de 403 en toda la app.
    Corregir al tocar `SecurityConfig`/manejo global de excepciones.
  - En `App.tsx`, las vistas `reports`/`ar`/`ap`/`settings` de `mainContent`
    no tienen guard `canAccessView` explícito (protegidas solo por el
    `useEffect` de redirección — posible flash de un frame).
  - `build.gradle` aún incluye `com.google.cloud.sql:postgres-socket-factory`
    (legado de Cloud SQL, ya decomisionado) — eliminar en la próxima pasada.

## 3. Análisis crítico (2026-06-11)

> Evaluación honesta del producto tal como está hoy, pensando en "¿qué le
> falta para que un negocio lo elija sobre Alegra, Odoo, QuickBooks o un
> sistema local?". Se revisó el código real de ambos repos, no solo este doc.

### 3.1 Fortalezas reales (lo que ya nos diferencia)

- **Dominio fiscal RD nativo:** NCF con secuencias por tipo, ITBIS
  (incluido/excluido con redondeo correcto), notas de crédito NCF 34,
  consulta de RNC/cédula contra DGII, QR en factura. Ningún ERP genérico
  trae esto bien resuelto out-of-the-box.
- **Vertical de servicios** (órdenes de servicio con Kanban, etapas,
  facturación parcial, depósitos) — encaja con fotografía, talleres,
  agencias; los POS genéricos no lo tienen.
- **Fundación modular seria:** feature flags por instancia
  (`FeatureCatalog` + V29), RBAC granular con overrides por usuario (V30),
  Centro de Módulos. Esto es arquitectura de producto, no de proyecto.
- **POS offline-first:** cola offline + service worker + sincronización
  tolerante. Crítico en RD (apagones, internet inestable) y raro en SaaS
  competidores.
- **Impresión térmica y carta** desde el navegador, sin drivers.
- **Contabilidad básica integrada** (catálogo de cuentas, asientos,
  sincronización desde ventas) — la mayoría de POS lo delegan a otro
  sistema.
- **Compras con recepción parcial, CxC/CxP con antigüedad, export Excel.**

### 3.2 Brechas críticas (en orden de gravedad comercial)

1. **Sin reportes DGII 606/607/608.** Toda empresa formal en RD debe
   enviarlos mensualmente. Hoy el cliente tendría que armarlos a mano fuera
   de ProStock → descalifica el producto para contabilidad formal. Es el
   gap nº 1 y además es barato de cerrar: ya tenemos todos los datos
   (compras con NCF de proveedor, ventas con NCF, anulaciones).
2. **Sin facturación electrónica (e-CF, Ley 32-23).** La DGII está
   obligando progresivamente a todos los contribuyentes a emitir e-CF. El
   que llegue con e-CF certificado barre el mercado de PyMEs; el que no lo
   tenga queda fuera en 1-2 años. Es un proyecto grande (certificados
   digitales, XML firmado, API DGII, contingencia) — hay que empezar por
   investigación/diseño ya.
3. **Calidad de pruebas insuficiente para vender.** Backend: 22 clases de
   test para 258 archivos / 25 controllers. Frontend: 3 archivos de test
   para ~30 vistas; sin suite e2e automatizada (Playwright se usa solo
   manualmente en sesiones). Cada release a un cliente real (Irisdicencia)
   es un acto de fe. Un referente comercial necesita regresión automatizada.
4. **Deuda técnica concentrada:** `POS.tsx` 2,453 líneas, `InventoryView`
   1,792, `Dashboard` 1,331, `api.ts` 1,710. Frena cada feature nueva y
   multiplica el riesgo de regresiones. La carpeta `components/` ya tiene
   subcarpetas por dominio — hay que terminar esa migración.
5. **Sin historia de despliegue multi-cliente.** Hoy: un stack Docker por
   cliente, configurado a mano (DS420+, demo local). No hay onboarding
   self-service, ni licenciamiento, ni telemetría central, ni gestión de
   versiones por cliente. Con 2 instancias se maneja; con 10 clientes no.
6. **Seguridad por debajo de estándar comercial:** sin 2FA, sin rate
   limiting en login, 401/403 confundidos, sin política de contraseñas ni
   expiración de sesiones configurable, secretos de integraciones aún sin
   diseño de cifrado.
7. **Sin multi-sucursal/multi-almacén.** Limita el techo de cliente
   (cadenas pequeñas, negocio con depósito + tienda).
8. **Sin cotizaciones** (presupuesto → conversión a factura) — flujo básico
   que casi todo prospecto pregunta.
9. **UX incompleta para producto:** sin modo oscuro, sin i18n (aceptable a
   corto plazo, ES-only), móvil solo responsive (sin app instalable
   promocionada — aunque el service worker ya existe, falta manifest/PWA
   pulida).

### 3.3 Dónde innovar (vanguardia, no checkbox)

- **IA aplicada al negocio (no chatbot decorativo):** pronóstico de demanda
  y punto de reorden por producto; detección de anomalías (caja, descuentos
  inusuales, fugas de inventario); asistente en lenguaje natural sobre los
  datos ("¿cuánto vendí de X en mayo vs abril?") usando los endpoints de
  reportes existentes; auto-categorización en el import CSV. Hay GPU local
  y experiencia previa (conectoria) para prototipar barato.
- **Pagos integrados RD:** tarjeta (Azul/CardNet/PixelPay), link de pago en
  el PDF/WhatsApp del estado de cuenta, conciliación automática del pago
  con la factura. Cobrar más rápido = argumento de venta directo.
- **Portal del cliente final:** el cliente del negocio ve sus facturas,
  estados de cuenta y órdenes de servicio (estado de su sesión de fotos),
  y paga online. Diferenciador enorme para el vertical de servicios.
- **WhatsApp como canal de primera clase** (Fase 6 ya planeada): no solo
  notificaciones — recordatorios de cobro con link de pago, confirmación de
  citas de órdenes de servicio.
- **Catálogo online ligado a inventario:** página pública de catálogo por
  instancia (sin llegar a e-commerce completo al inicio).

## 4. Fundación existente (Fase 0-1, ya implementada)

### Feature flags por instancia (Fase 1)
- `feature/FeatureCatalog.java` — catálogo de features `module.*` y
  sub-features, con categorías, dependencias y `defaultEnabled`.
- Tabla `company_feature_config` (V29) — overrides por instancia, solo se
  guarda fila cuando difiere del default.
- UI en `ModulesView` (Centro de Módulos) — gated por
  `settings.manage_features`. `Sidebar` filtra navegación vía
  `VIEW_TO_FEATURE`.

### Permisos granulares (Fase 1)
- Tablas `permissions` / `role_permissions` (V30) — matriz rol → permisos,
  UI en `RolesPermissionsView`, gated por `settings.manage_permissions`.
- Tabla `user_permission_overrides` (V30) + endpoints
  `/api/permissions/users/{userId}/overrides` — overrides individuales
  (Denegar/Heredar/Otorgar), UI en `UserPermissionOverridesModal`.
- `GESTOR` = superusuario, bypass total por código
  (`PermissionServiceImpl.getEffectivePermissions`).

### Dashboard (Fase 4.1)
- 3 secciones lógicas con drag & drop (dnd-kit) y mostrar/ocultar por
  widget, persistido en `localStorage`. Filtro de rango de fechas global
  (Hoy/7d/30d/Personalizado). Saludo + empresa en el Sidebar.

### Dominio ya cubierto (estado 2026-06-11, migraciones hasta V33)
- Ventas multi-pago (PENDING/PARTIALLY_PAID/COMPLETED), NCF, QR,
  notas de crédito (NCF 34), órdenes de servicio con facturación parcial y
  descuento de stock configurable, compras con recepción parcial,
  CxC/CxP con antigüedad y recordatorios, contabilidad básica (cuentas,
  asientos, sync), auditoría, import CSV de catálogo, export Excel,
  reportes de margen/rotación, POS offline.

## 5. Hoja de ruta — re-priorizada con enfoque comercial (2026-06-11)

> Tres carriles en paralelo: **C (Comercial/Cumplimiento)** — lo que hace
> vendible el producto; **Q (Calidad/Plataforma)** — lo que lo hace
> sostenible; **F (Features/fases originales)** — lo que lo hace más
> completo. Regla práctica: **no sumar una fase F grande sin avanzar algo
> de C o Q en la misma ventana de trabajo.** Cada fase se detalla en
> checklist justo antes de empezarla.

### Carril C — Cumplimiento y comercialización

#### Fase C1 — Reportes DGII 606/607/608 ⭐ máxima prioridad — 🟡 EN CURSO
- [x] **(2026-06-11)** Generación del 607 (ventas) desde `Sale`/NCF +
      notas de crédito del período: `DgiiReportService` con preview JSON
      (`GET /api/reports/dgii/607?period=YYYY-MM`) y TXT formato de envío
      pipe-delimited (`/607/txt`), con desglose de formas de pago desde
      `SalePayment` (efectivo/cheque-transferencia/tarjeta/crédito/otras).
- [x] **(2026-06-11)** 608 (NCF anulados): ventas CANCELED con NCF del
      período (`/api/reports/dgii/608` + `/608/txt`, tipo anulación 04 por
      defecto). Hoy normalmente vacío: las COMPLETED no se pueden cancelar
      (las devoluciones van por nota de crédito al 607) — correcto.
- [x] **(2026-06-11)** Sección "Cumplimiento DGII" en Reportes
      (`DgiiComplianceCard`): selector de mes persistido, KPIs del período,
      preview del 607 y descarga TXT de ambos. Protegido por `view.reports`
      (se reutilizó el permiso existente en vez de crear `reports.dgii`).
- [x] Tests unitarios del servicio (4 casos: 607 con ventas+NC y desglose
      de pagos, layout TXT, 608, consumidor sin documento).
- [x] **(2026-06-11)** Generación del 606 (compras): migración **V34**
      (RNC/tipo identificación en `suppliers`; NCF proveedor, tipo
      bienes/servicios 01-11 (default 09), ITBIS, fecha de pago y forma de
      pago en `purchase_orders`). El 606 incluye órdenes con NCF del período,
      separando montos de bienes vs servicios según
      `Product.tipoBienServicio`. `fechaPago`/`paymentMethod` se setean
      automáticamente al saldar la orden (`addPayment`); forma de pago 04
      (a crédito) si no está saldada. Captura en UI: campos NCF/ITBIS en
      `PurchaseOrderModal`, RNC/tipo identificación en `SuppliersView` (y
      `dgiiToSupplierPayload` los llena desde la consulta DGII). Botón y
      KPI 606 en `DgiiComplianceCard`.
- [ ] ⚠️ **Validar los TXT (606/607/608) con la Herramienta de Envío /
      pre-validador de la DGII antes de la primera remisión real** (el
      layout sigue la Norma 07-18; confirmar contra la herramienta vigente).
- [ ] Gastos sin orden de compra (gastos menores/servicios sin OC) — hoy el
      606 solo cubre compras con OC; evaluar entidad de gasto simple.
- [ ] Validaciones previas al cierre de mes (NCF faltantes, RNC inválidos).
- [ ] Backfill: proveedores existentes no tienen RNC estructurado (estaba
      solo en el texto de dirección) — editarlos una vez desde la UI.

#### Fase C2 — Facturación electrónica e-CF (Ley 32-23) — ⏸️ EN PAUSA
> **(2026-06-11)** El proceso de certificación e-CF requiere tener el
> software en venta/operación primero. Esta fase queda **bloqueada por un
> proceso externo**: el usuario la retomará cuando reciba el visto bueno.
> Mientras tanto, el resto del roadmap continúa sin depender de ella.
- [ ] **Spike (1 sesión):** leer normativa técnica vigente de DGII (formato
      XML e-CF, firma digital, API de recepción, ambientes de prueba,
      calendario de obligatoriedad por tipo de contribuyente) y decidir:
      certificarse directo vs integrar un proveedor de e-CF autorizado.
- [ ] Diseño: dónde viven los certificados por empresa, contingencia
      offline, numeración e-CF vs NCF tradicional (convivencia).
- [ ] Implementación por etapas (emisión 31/32, luego 33/34, anulaciones).
- [ ] Esto convierte a ProStock en candidato serio para *cualquier* PyME
      formal de RD — es la apuesta comercial más grande del roadmap.

#### Fase C3 — Operación multi-cliente (de "instalaciones" a "producto")
- [ ] Decidir modelo: instancias self-hosted gestionadas (actual) vs SaaS
      multi-tenant real. Recomendación inicial: **formalizar el modelo de
      instancias** (más barato, ya funciona) antes de plantear multi-tenant.
- [ ] Plantilla de despliegue reproducible (compose + script de alta de
      cliente: dominio, credenciales, seeds, backup).
- [ ] Versionado y canal de releases (tags semánticos, changelog,
      Watchtower apuntando a tags estables, no `latest`).
- [ ] Telemetría mínima opt-in por instancia (versión, salud, error rate)
      hacia un endpoint central propio.
- [ ] Backups verificados por instancia (hoy: script en DS420+; falta
      restore-test periódico).
- [ ] Licenciamiento básico (activación por instancia, expiración, módulos
      contratados — se apoya en los feature flags existentes).

#### Fase C4 — Pagos integrados
- [ ] Investigar pasarelas RD: Azul, CardNet, PixelPay (+ Stripe para
      tarjetas internacionales). Elegir 1 para MVP.
- [ ] Link de pago en estado de cuenta / recordatorios (email/WhatsApp).
- [ ] Conciliación automática: pago online → `SalePayment` → posible
      auto-complete (lógica ya existente).
- [ ] Feature `module.payments`, credenciales cifradas por empresa.

### Carril Q — Calidad y plataforma

#### Fase Q1 — Red de seguridad de pruebas ⭐ hacer antes de crecer más
- [ ] Suite e2e Playwright commiteada y en CI: login, venta completa POS
      (con y sin cliente), pago parcial → completar, nota de crédito, orden
      de servicio → facturar, permisos (cashier no ve inventario — regresión
      del bug de Fase 3).
- [ ] Backend: tests de servicio para flujos fiscales (NCF, ITBIS
      incluido/excluido, nota de crédito, auto-complete de pagos) — son los
      que no pueden fallar nunca.
- [ ] Gate de CI: e2e + tests verdes para mergear a `main`.
- [ ] Datos de prueba seed reproducibles (incluye usuario `cashier` de
      pruebas — resuelve el bloqueo de validación por roles de Fase 4.1).

#### Fase Q2 — Pago de deuda técnica frontend
- [ ] Partir `api.ts` (1,710 líneas) por dominio (`api/sales.ts`,
      `api/inventory.ts`, …) manteniendo re-exports para no romper imports.
- [ ] Descomponer `POS.tsx` (2,453 líneas): checkout sheet, facturas
      abiertas, cliente rápido y carrito como componentes/hooks propios.
- [ ] `InventoryView` (1,792) y `Dashboard` (1,331): misma medicina,
      aprovechando las subcarpetas por dominio ya creadas.
- [ ] Lazy-loading por vista (code splitting) — hoy todo va en un bundle.

#### Fase Q3 — Hardening de seguridad
- [ ] `AccessDeniedException` → 403 (issue conocido).
- [ ] Guards `canAccessView` explícitos en `reports`/`ar`/`ap`/`settings`.
- [ ] Rate limiting en `/api/auth/login` + lockout temporal.
- [ ] 2FA TOTP opcional por usuario (obligatorio configurable para ADMIN).
- [ ] Política de contraseñas + expiración de sesión configurables.
- [ ] Diseño de cifrado de credenciales de integraciones (necesario para
      C4 y Fase 6).
- [ ] Limpiar dependencia legacy `postgres-socket-factory` (GCP).

### Carril F — Features (continuación de fases originales)

#### Fase 4.2 — KPIs nuevos de dashboard (requiere backend)
- [ ] Antigüedad CxC/CxP (0-30/31-60/61-90/90+) — el backend ya calcula
      aging en CxC/CxP views; exponer agregado para dashboard.
- [ ] Top productos y top clientes del período.
- [ ] Flujo de caja (ingresos vs egresos en el tiempo).
- [ ] Comparación vs período anterior (variación %).

#### Fase 4.3 — Layouts de dashboard por rol
- [ ] Defaults por rol sobre el sistema de personalización de 4.1.

#### Fase 4.4 — Exportar/imprimir dashboard
- [ ] PDF/imagen del resumen.

#### Fase 5 — Empleados / RRHH
- [ ] Entidad `Employee` (opcionalmente ligada a `User`), asistencia,
      comisiones por venta, nómina básica (TSS/ISR simplificado RD).
- [ ] Feature `module.employees`, categoría de permisos `EMPLOYEES`.

#### Fase 6 — Integraciones WhatsApp / redes
- [ ] Notificaciones de venta/factura, alertas de stock bajo, recordatorios
      de cobro **con link de pago (sinergia con C4)**.
- [ ] Credenciales cifradas por empresa (depende del diseño de Q3).
- [ ] Reutilizar patrones de `~/code/conectoria/integrations/whatsapp`.

#### Fase 7 — Branding por instancia
- [ ] Logo/colores por empresa, plantillas de factura personalizables.

#### Fase 8 — UI/UX general
- [ ] Modo oscuro, revisión de design system, responsividad móvil,
      manifest PWA pulido (instalable; el service worker ya existe).

#### Fase 9 — Reportes avanzados / BI
- [ ] Reportes programados (email mensual al dueño), más exports.

#### Fase 10 — Cotizaciones
- [ ] Documento cotización → conversión a venta/factura con un clic,
      vigencia, estados (enviada/aceptada/vencida). Flujo muy pedido.

#### Fase 11 — Multi-sucursal / multi-almacén
- [ ] Almacenes múltiples, stock por almacén, transferencias, ventas por
      sucursal en reportes. Cambio de modelo de datos grande — diseñar
      con calma; sube el techo de cliente alcanzable.

#### Fase 12 — Portal del cliente final
- [ ] Acceso del cliente del negocio a sus facturas, estados de cuenta y
      estado de órdenes de servicio; pago online (depende de C4).

#### Fase 13 — IA aplicada
- [ ] Pronóstico de demanda / sugerencia de reorden por producto.
- [ ] Detección de anomalías (descuentos inusuales, mermas).
- [ ] Asistente de consultas en lenguaje natural sobre reportes.
- [ ] Auto-categorización en import CSV.
- [ ] Prototipar con GPU local / experiencia de conectoria; decidir
      proveedor LLM por costo cuando haya un caso validado.

## 6. Convenciones de trabajo

- Este documento se mantiene en español, igual que el resto de
  `documentacion/`.
- Antes de empezar una fase nueva: convertir su esbozo en un checklist de
  tareas concretas.
- Al cerrar una fase: marcar los checkboxes y anotar en la Bitácora
  (sección 7) los commits (hash + repo) que la implementaron.
- Commits y push siguen el flujo ya establecido: conventional commits en
  español, push a ambos repos (`proStock` y `proStockFront`) cuando el
  usuario lo confirme.
- **Regla nueva (2026-06-11):** al planificar una sesión, mirar los tres
  carriles (C/Q/F) — evitar acumular solo features sin cerrar brechas de
  cumplimiento o calidad.

## 7. Bitácora de sesiones

### 2026-06-10
- Completada Fase 1: overrides de permisos por usuario (backend + UI),
  pusheado (`proStock@705581b`, `proStockFront@40c6818`).
- Creado este plan de ejecución (`proStock@3c0c37f`).
- Implementada y pusheada Fase 2 (`proStockFront@d356601`): `ModulesView`,
  `lib/modules.ts`, pestaña "Módulos" en `SettingsView`.
- Implementada y pusheada Fase 3 (`proStockFront@8e5ac84`,
  `proStock@0098db3`): command palette (14 vistas, filtrado por
  permiso/feature flag, navegación por teclado), Favoritos en Sidebar.
  Durante pruebas se encontró y corrigió **bug de seguridad**: usuarios sin
  permisos de inventario (ej. `cashier`) eran redirigidos a
  `/inventory/articles` y veían el `InventoryView` completo. Fix en
  `App.tsx` (efecto scoped a `view === "inventory"` + guards
  `canAccessView` en `pos`/`invoice`/`inventory`).
- Fase 4 dividida en 4.1-4.4. Implementada **Fase 4.1** (100% frontend):
  saludo/empresa al Sidebar (`getGreeting()`), filtro de fechas global
  persistido, 3 secciones con drag & drop, mostrar/ocultar widgets.
  **Bug corregido:** el filtro mandaba `YYYY-MM-DD` pero
  `/api/sales/summary` espera ISO `DATE_TIME` → 400 silencioso, KPIs en
  cero. Validación visual `cashier` omitida (sin credenciales de prueba —
  ver Fase Q1, seeds de prueba).

### 2026-06-11
- (Vía Cursor, fuera de sesión Claude) Gran avance funcional, todo
  commiteado y pusheado:
  - `proStock@f881240`: notas de crédito NCF 34 (V31/V32), recepción
    parcial de OC, filtro `overdueOnly`, paginación de movimientos.
  - `proStock@54534cc`: órdenes de servicio — KPIs, reportes, descuento de
    stock configurable (V33), facturación parcial, feature flag + permisos.
  - `proStockFront@54f466b` y `@71cddb7` (rama `continue-screens`):
    movimientos, import CSV, CxC/CxP con antigüedad, recordatorios,
    PDF estado de cuenta, notas de crédito UI, reportes margen/rotación,
    integración transversal de órdenes de servicio, Kanban con deep links,
    offline sync más tolerante.
- (Sesión Claude) **Reescritura de este plan**: nuevo análisis crítico
  (sección 3) tras revisar el código real de ambos repos, y hoja de ruta
  re-priorizada en 3 carriles (C/Q/F) con foco comercial. Hallazgos clave:
  faltan 606/607/608 y e-CF (gaps comerciales nº 1 y 2), cobertura de
  tests insuficiente (22 clases backend / 3 archivos frontend, sin e2e en
  CI), deuda técnica concentrada (`POS.tsx` 2,453 líneas, `api.ts` 1,710),
  sin historia de operación multi-cliente ni licenciamiento. Fortalezas
  confirmadas: dominio fiscal RD, vertical de servicios, modularidad,
  POS offline, impresión térmica, contabilidad integrada.
- Pendiente al cierre: merge de `continue-screens` → `main` en
  `proStockFront`; decidir próxima fase (recomendado: C1 o Q1).
- Usuario aclaró que la **Fase C2 (e-CF) queda en pausa**: el proceso de
  certificación requiere tener el software en venta primero; se retoma con
  su visto bueno. Plan pusheado (`proStock@2af9fe5`).
- **Arranque de Fase C1 (reportes DGII):** implementados **607 y 608**:
  - Backend: `DgiiReportService`/`Impl` (preview DTO + render TXT formato de
    envío), `DgiiReportController` (`/api/reports/dgii/{607,608}[/txt]`,
    `@PreAuthorize` con `view.reports`), queries nuevas en `SaleRepository`
    (ventas con NCF por estado/rango), `CreditNoteRepository` (rango con
    fetch de venta/cliente) y `SalePaymentRepository` (`findBySaleIdIn`).
    607 incluye ventas COMPLETED con NCF + notas de crédito (NCF modificado),
    con desglose de formas de pago y "venta a crédito" = saldo no cobrado.
    Sin migraciones — todos los datos ya existían.
  - Frontend: `DgiiComplianceCard` en Reportes — selector de mes (persistido
    en `localStorage`), KPIs (registros 607, monto, ITBIS, anulados 608),
    tabla preview del 607 y botones de descarga TXT. Tipos y funciones
    nuevas en `api.ts` (`getDgii607Report`/`getDgii608Report`/
    `downloadDgiiTxt`).
  - Validación: 4 tests unitarios nuevos (`DgiiReportServiceImplTest`);
    suite backend completa verde (`DB_PASSWORD=admin ./gradlew test`);
    frontend `npm run validate` verde.
  - Pendiente de C1: 606 (capturar NCF de proveedor en compras), validar el
    TXT contra el pre-validador oficial de DGII, validaciones de cierre de
    mes.
- **Continuación: 606 (compras) implementado.** Migración **V34**
  (`suppliers.rnc_cedula`/`tipo_identificacion`;
  `purchase_orders.ncf_proveedor`/`tipo_bienes_servicios`/`total_itbis`/
  `fecha_pago`/`payment_method`). Backend: `build606`/`render606Txt` en
  `DgiiReportService` (bienes vs servicios según `Product.tipoBienServicio`,
  forma de pago 01-07, 04 si está a crédito), endpoints `/api/reports/dgii/
  606[/txt]`; `addPayment` de OC ahora setea `fechaPago`/`paymentMethod` al
  saldar; create/update de OC aceptan los campos nuevos. Frontend: campos
  NCF proveedor + ITBIS en `PurchaseOrderModal`, RNC/tipo identificación en
  `SuppliersView` y en `dgiiToSupplierPayload`, 606 en `DgiiComplianceCard`.
  Validación: 2 tests nuevos del 606 (split bienes/servicios + layout TXT,
  compra a crédito); suite backend completa y `npm run validate` verdes.
  Notas: el 606 solo cubre compras con OC (gastos menores pendiente);
  proveedores existentes requieren backfill manual del RNC.
