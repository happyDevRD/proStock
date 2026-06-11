# Plan de ejecución — ProStock ERP Modular

> **Documento vivo.** Se actualiza al final de cada sesión de trabajo. Si el
> contexto se corta o empezamos una sesión nueva, lee primero la sección
> **"2. Estado actual / próximo paso"** para retomar exactamente donde quedamos.

## 1. Visión

(Planteada por el usuario, 2026-06-10)

ProStock deja de ser un simple sistema de ventas/POS y pasa a ser un **ERP
modular estilo Odoo, con mejoras y funcionalidades específicas propias**.
Objetivos concretos:

- Reorganizar mejor el contenido y el menú.
- Facilitar y ampliar funcionalidades existentes.
- Interfaz más amigable.
- Dashboard muchísimo más completo.
- **Lo más importante: modularizar la aplicación** — poder
  habilitar/deshabilitar funciones por usuario/instancia, con opciones de
  personalización (ej. gestión de empleados, integraciones con WhatsApp o
  redes sociales, y más módulos a futuro).

## 2. Estado actual / próximo paso

- **Última actualización:** 2026-06-10
- **Fase 1 (fundación de modularización): ✅ COMPLETADA y pusheada**
  - Backend `proStock` commit `705581b`
  - Frontend `proStockFront` commit `40c6818`
- **Fase 2 (Centro de Módulos): ✅ COMPLETADA y pusheada**
  - Frontend `proStockFront` commit `d356601`
- **Fase 3 (Menú y navegación): ✅ COMPLETADA y pusheada**
  - Frontend `proStockFront` commit `8e5ac84`
  - Docs `proStock` commit `0098db3`
  - `TopBar.tsx`: command palette ampliado a las 14 vistas, filtrado por
    permiso (`canAccessView`) y feature flag (`VIEW_TO_FEATURE`/
    `isFeatureEnabled`), con navegación real por teclado (↑/↓/Enter, Esc para
    cerrar).
  - `Sidebar.tsx`: Favoritos — estrella al hacer hover sobre cada ítem,
    persistidos en `localStorage` (`prostock.ui.favoriteViews`), con grupo
    "Favoritos" fijo arriba del menú.
  - `NAV_GROUPS`/`navItems` revisados: la agrupación actual (Principal /
    Operaciones / Gestión / Servicios / Análisis / Sistema) sigue siendo
    adecuada para los módulos planeados; no se requirieron cambios.
  - **Bug de seguridad encontrado y corregido durante las pruebas**: un
    usuario sin permisos `inventory.module.*` (ej. `cashier`) era redirigido
    permanentemente a `/inventory/articles` y veía el `InventoryView`
    completo (tabla editable de productos) al iniciar sesión. Causa: un
    `useEffect` en `App.tsx` validaba `canAccessInventoryModule` en cada
    render sin importar la vista activa (porque `pathToLocation` siempre
    devuelve `inventoryModule: "articles"` para rutas que no son
    `/inventory/*`). Corregido: el efecto ahora solo aplica cuando
    `view === "inventory"`, y se agregaron guards
    `canAccessView(authUser, "...")` a `pos`, `invoice` e `inventory` en
    `mainContent` (igual que `suppliers`/`customers`/`users`/etc.).
  - Validado: typecheck + lint + test + build verdes. Probado visualmente con
    Playwright/Chromium para roles `admin` y `cashier`.
- **Fase 4.1 (Dashboard — reorganización y personalización): ✅ COMPLETADA,
  pendiente push**
  - Frontend `proStockFront` — sin commit/push aún (pendiente confirmación
    del usuario).
  - Saludo + nombre de empresa movidos del Dashboard al Sidebar
    (`getGreeting()` en `Sidebar.tsx`).
  - Filtro de rango de fechas global (Hoy/7d/30d/Personalizado), persistido
    en `localStorage`; KPIs de "Resumen del período"
    (ventas/ingresos/ticket) ahora usan `completedCount`/`completedRevenue`/
    `averageTicket` filtrados por el rango, en vez de los valores fijos de
    "hoy".
  - Reorganización en 3 secciones lógicas: "Resumen del período" / "Estado
    del negocio" / "Accesos rápidos", cada una con su propio drag & drop
    (mismo esquema de `localStorage` plano, sin migración).
  - Personalización mostrar/ocultar widgets (KPIs y bloques de contenido),
    persistida en `localStorage`, con UX atenuada en modo edición.
  - **Bug de integración encontrado y corregido**: el nuevo filtro de fechas
    enviaba `startDate`/`endDate` como `YYYY-MM-DD`, pero
    `/api/sales/summary` espera `LocalDateTime` en formato ISO `DATE_TIME`
    completo → causaba HTTP 400 silenciosos y KPIs de período en cero.
    Corregido en frontend convirtiendo a ISO datetime (mismo patrón que
    `ReportsView.tsx`); sin cambios de backend.
  - Validado: typecheck/lint/test/build verdes (6/6 tests). Verificación
    visual con Playwright/Chromium como `admin` (presets de fecha, modo
    edición, ocultar/mostrar con persistencia tras recargar, las 3
    secciones). **Verificación con rol `cashier` omitida deliberadamente**
    (el cambio no toca lógica de permisos/roles de la Fase 3; ver sección 5
    de la Fase 4 más abajo para el detalle).
- **Próximo paso:** decidir si seguimos con Fase 4.2 (KPIs nuevos:
  antigüedad CxC/CxP, top productos/clientes, flujo de caja, comparación de
  períodos — requiere backend), Fase 4.3/4.4, o Fase 5 (Empleados/RRHH).
- **Issue conocido, no bloqueante:** `AccessDeniedException` devuelve HTTP
  401 en vez de 403 en toda la app (confirmado de nuevo en esta sesión).
  Corregir la próxima vez que se toque `SecurityConfig`/manejo global de
  excepciones.
- **Nuevo issue conocido, no bloqueante:** en `App.tsx`, las vistas
  `reports`, `ar`, `ap` y `settings` dentro de `mainContent` aún no tienen un
  guard `canAccessView` explícito (a diferencia de `pos`/`invoice`/
  `inventory`/`suppliers`/`customers`/`users`/`service_orders`/`accounting`/
  `setup`, que sí lo tienen tras el fix de esta sesión). El `useEffect` de
  redirección por vista (~línea 198) ya las protege, pero podría haber un
  flash de un frame antes de redirigir. Revisar en una futura pasada de
  hardening de permisos.

## 3. Fundación existente (Fase 0-1, ya implementada)

### Feature flags por instancia (Fase 1)
- `feature/FeatureCatalog.java` — catálogo de features `module.*` y
  sub-features, con categorías, dependencias y `defaultEnabled`.
- Tabla `company_feature_config` (V29) — overrides por instancia, solo se
  guarda fila cuando difiere del default.
- UI en `SettingsView` — toggle por categoría, muestra dependencias/
  dependientes, gated por permiso `settings.manage_features`.
- `Sidebar` filtra ítems de navegación vía `VIEW_TO_FEATURE`.

### Permisos granulares (Fase 1)
- Tablas `permissions` / `role_permissions` (V30) — matriz rol → permisos,
  UI en `RolesPermissionsView`, gated por `settings.manage_permissions`.
- Tabla `user_permission_overrides` (V30) + endpoints
  `/api/permissions/users/{userId}/overrides` — overrides individuales por
  usuario (Denegar/Heredar/Otorgar), UI en `UserPermissionOverridesModal`
  desde `UsersView`.
- `GESTOR` = superusuario, bypass total por código
  (`PermissionServiceImpl.getEffectivePermissions`).

### Dashboard
- `Dashboard.tsx` — 3 secciones lógicas ("Resumen del período", "Estado del
  negocio", "Accesos rápidos"), cada una con sus widgets reordenables por
  drag & drop (dnd-kit) y mostrar/ocultar, persistidos en `localStorage`
  (Fase 4.1).
- Filtro de rango de fechas global (Hoy/7d/30d/Personalizado) que alimenta
  los KPIs y el gráfico de "Resumen del período".
- KPIs actuales: ventas/ingresos/ticket promedio (filtrados por período),
  borradores/stock/CxC (estado actual, sin filtro de fecha).
- Bloques de contenido: gráfico de ventas, accesos directos, ventas
  recientes, estado, stock crítico.
- Saludo + nombre de empresa viven en el Sidebar, no en el Dashboard.

### Navegación
- `Sidebar.tsx` agrupa vistas en: Principal / Operaciones / Gestión /
  Servicios / Análisis / Sistema. Colapsable, con submenús para
  inventario/facturas/contabilidad.

## 4. Hoja de ruta propuesta

> Cada fase se detalla con más profundidad **justo antes de empezarla**. Las
> fases que aún no se han iniciado solo tienen un esbozo de alcance.

### Fase 2 — Centro de Módulos ✅ (frontend implementado, pendiente push)
**Objetivo:** convertir la lista de feature flags de `SettingsView` en una
pantalla tipo "marketplace" (módulos activos / disponibles / próximamente).
Esta pantalla será la puerta de entrada para registrar y activar todos los
módulos nuevos de las fases siguientes.

- [x] Backend: se evaluó agrupar `FeatureCatalog` en "módulos" de primer
      nivel con metadata extra (ícono, estado beta/próximamente), pero el
      DTO existente (`code`/`category`/`name`/`description`/`enabled`/
      `dependsOn`/`dependenciesSatisfied`) ya alcanza para agrupar por
      categoría y detectar la raíz `module.*` en frontend — **no se
      necesitó migración ni cambios de backend**.
- [x] Frontend: nueva vista `ModulesView` (tarjetas por módulo, toggle,
      sub-features con badges de dependencias, sección "Próximamente").
- [x] Mover el toggle de features de `SettingsView` a esta nueva vista
      (Settings queda para configuración de empresa/NCF/etc.).
- [x] Reutilizar permiso `settings.manage_features` (modo solo lectura si
      no se tiene el permiso, igual que Roles y Permisos).

### Fase 3 — Menú y navegación ✅ COMPLETADA
- [x] Revisar agrupación de `NAV_GROUPS` pensando en los módulos nuevos —
      revisado, no se requirieron cambios.
- [x] Buscador rápido / command palette (atajo `/`) para saltar entre
      vistas — ampliado a las 14 vistas con filtrado por permiso/feature flag
      y navegación por teclado.
- [x] Favoritos / accesos pinneados por usuario — implementado en
      `Sidebar.tsx`, persistido en `localStorage`.
- [x] (extra, encontrado durante pruebas) Corregido bug de permisos: usuarios
      sin acceso a inventario ya no son redirigidos ni ven `InventoryView`.

### Fase 4 — Dashboard mucho más completo

> Dividida en sub-fases por tamaño. 4.1 es 100% frontend (el endpoint
> `/api/sales/summary` ya devolvía los campos necesarios); 4.2 requiere
> entidades/repos/endpoints nuevos en `proStock`.

#### Fase 4.1 — Reorganización y personalización ✅ COMPLETADA
- [x] Mover saludo + nombre de empresa del Dashboard al Sidebar
      (`getGreeting()`).
- [x] Filtro de rango de fechas global (Hoy/7d/30d/Personalizado),
      persistido en `localStorage`, con KPIs de período (ventas/ingresos/
      ticket) usando `completedCount`/`completedRevenue`/`averageTicket` de
      `/api/sales/summary`.
- [x] Reorganización en 3 secciones lógicas ("Resumen del período" /
      "Estado del negocio" / "Accesos rápidos") con drag & drop por sección.
- [x] Personalización mostrar/ocultar widgets (KPIs y bloques de
      contenido), persistida en `localStorage`.
- [x] Fix de formato de fecha en `/api/sales/summary` (ISO `DATE_TIME` en
      vez de `YYYY-MM-DD`), siguiendo el patrón de `ReportsView.tsx`.

#### Fase 4.2 — KPIs nuevos (requiere backend)
- [ ] Antigüedad de cuentas por cobrar/pagar (CxC/CxP aging: 0-30/31-60/
      61-90/90+ días).
- [ ] Top productos y top clientes (por ingresos/cantidad, en el período
      seleccionado).
- [ ] Flujo de caja (ingresos vs egresos en el tiempo).
- [ ] Comparación vs. período anterior (variación % sobre los KPIs de
      "Resumen del período").

#### Fase 4.3 — Layouts por rol
- [ ] Layouts por defecto según rol (cajero ve ventas, admin ve financiero),
      construidos sobre el sistema de personalización (orden/visibilidad)
      de la Fase 4.1.

#### Fase 4.4 — Exportar/imprimir
- [ ] Exportar/imprimir resumen del dashboard (PDF/imagen).

### Fase 5 — Módulo de Empleados / RRHH
- [ ] Entidad `Employee` (opcionalmente ligada a `User`).
- [ ] Control de asistencia básico (entrada/salida).
- [ ] Comisiones por venta (ligar a `Sale`).
- [ ] Cálculo básico de nómina (TSS / ISR simplificado para RD).
- [ ] Nueva categoría de permisos `EMPLOYEES`, feature `module.employees`.

### Fase 6 — Integraciones (WhatsApp / redes sociales)
- [ ] Notificaciones de venta/factura por WhatsApp Business API.
- [ ] Alertas de stock bajo a administradores.
- [ ] Configuración de credenciales por empresa (cifradas).
- [ ] Evaluar reutilizar patrones de `~/code/conectoria/integrations/whatsapp`.
- [ ] Feature `module.integrations.whatsapp`.

### Fase 7 — Personalización / branding por instancia
- [ ] Logo y colores de marca por empresa (extender `companyConfig`).
- [ ] Plantillas de factura personalizables.

### Fase 8 — Rediseño UI/UX general
- [ ] Revisión de design system / componentes compartidos.
- [ ] Modo oscuro.
- [ ] Mejoras de responsividad móvil.

### Fase 9 — Reportes avanzados / BI
- [ ] Exportar a Excel/PDF.
- [ ] Reportes programados.

## 5. Convenciones de trabajo

- Este documento se mantiene en español, igual que el resto de
  `documentacion/`.
- Antes de empezar una fase nueva: convertir su esbozo en un checklist de
  tareas concretas.
- Al cerrar una fase: marcar los checkboxes y anotar en la Bitácora (sección
  6) los commits (hash + repo) que la implementaron.
- Commits y push siguen el flujo ya establecido: conventional commits en
  español, push a ambos repos (`proStock` y `proStockFront`) cuando el
  usuario lo confirme.

## 6. Bitácora de sesiones

### 2026-06-10
- Completada Fase 1: overrides de permisos por usuario (backend + UI),
  pusheado (`proStock@705581b`, `proStockFront@40c6818`).
- Creado este plan de ejecución (`proStock@3c0c37f`).
- Usuario eligió Fase 2 (Centro de Módulos) como siguiente paso.
- Implementada Fase 2 en frontend: `ModulesView.tsx`, `lib/modules.ts`,
  pestaña "Módulos" en `SettingsView`. Typecheck/lint/test/build verdes.
  Pendiente: commit + push, y validación visual en navegador.
- Fase 2 pusheada (`proStockFront@d356601`).
- Usuario eligió continuar con Fase 3 (Menú y navegación).
- Implementada Fase 3 en frontend: command palette ampliado (14 vistas,
  filtrado por permiso/feature flag, navegación por teclado) en `TopBar.tsx`;
  Favoritos persistidos en Sidebar; revisión de `NAV_GROUPS` (sin cambios).
- Durante las pruebas (Playwright, rol `cashier`) se encontró y corrigió un
  bug de permisos: usuarios sin acceso a inventario eran redirigidos
  permanentemente a `/inventory/articles` y veían el `InventoryView`
  completo. Fix en `App.tsx` (efecto de módulo de inventario scoped a
  `view === "inventory"`, + guards `canAccessView` en `pos`/`invoice`/
  `inventory`).
- Typecheck/lint/test/build verdes; validación visual con Playwright para
  roles `admin` y `cashier`.
- Fase 3 pusheada (`proStockFront@8e5ac84`, `proStock@0098db3` docs). CI
  publicó nueva imagen frontend a GHCR. Demo local `prostock-happydevs`
  (localhost:8090) reconstruida y verificada con la nueva imagen. DS420+
  (Irisdicencia) queda en imagen anterior, pendiente de actualización vía
  Watchtower (medianoche) o despliegue manual cuando se levante el túnel VPN.
- Usuario eligió Fase 4 (Dashboard) y amplió el alcance: quería todas las
  mejoras de Fase 4 (KPIs nuevos, filtros de fecha, layouts por rol,
  exportar) **más** mover saludo/nombre de empresa al Sidebar, reorganizar
  en secciones lógicas, y permitir personalizar qué widgets se ven. Por el
  tamaño se dividió en sub-fases 4.1-4.4 (ver sección 4); esta sesión cubrió
  **Fase 4.1** completa (100% frontend):
  - `Sidebar.tsx`: nuevo `getGreeting()`, reemplaza la etiqueta estática
    "ProStock" en la sección Brand.
  - `Dashboard.tsx`: filtro de rango de fechas global (Hoy/7d/30d/
    Personalizado) persistido en `localStorage`
    (`prostock.dashboard.dateRange`); `ChartWidget` ahora recibe
    `summary`/`isLoading`/`fallbackSeries` por props (sin `useQuery` propio);
    KPIs `kpi-ventas/ingresos/ticket` pasan de `today*` a
    `completedCount`/`completedRevenue`/`averageTicket` filtrados por el
    rango; `kpi-borradores/stock/cxc` sin cambios (estado actual).
  - Reorganización en 3 secciones ("Resumen del período" / "Estado del
    negocio" / "Accesos rápidos") vía nuevo componente `DashboardSection`,
    con drag & drop por sección sobre los mismos arrays planos
    `kpiOrder`/`contentOrder` (sin migración de `localStorage`).
  - Personalización mostrar/ocultar: nuevas claves
    `prostock.dashboard.hiddenKpis`/`hiddenContent`; `SortableItem` agrega
    botón Eye/EyeOff visible en modo edición; widgets ocultos se atenúan
    (`opacity-50`) en edición y desaparecen del todo fuera de edición;
    sección sin widgets visibles colapsa (`return null`) salvo en modo
    edición, donde muestra un aviso para reactivarlos.
  - **Bug encontrado y corregido**: el filtro de fechas mandaba
    `startDate`/`endDate` como `YYYY-MM-DD` a `/api/sales/summary`, pero el
    controller (`SaleController.java`, `@DateTimeFormat(iso=DATE_TIME)
    LocalDateTime`) requiere ISO datetime completo → 400 silencioso, KPIs de
    período en cero pese a que el gráfico (con datos de fallback) sí mostraba
    valores. Corregido convirtiendo a `toISOString()` (inicio) y
    `T23:59:59` + `toISOString()` (fin), igual que `ReportsView.tsx`. Sin
    cambios de backend.
  - Validado: `npm run typecheck`/`lint`/`test` (6/6)/`build` verdes.
    Verificación visual con Playwright/Chromium como `admin`: presets de
    fecha, KPIs de período actualizándose, modo "Personalizar"/"Listo",
    ocultar/mostrar con persistencia tras recargar, render de las 3
    secciones (incluyendo Atajos a `col-span-12`).
  - **Pendiente deliberadamente omitido**: verificación visual con rol
    `cashier` — el cambio no toca lógica de permisos/roles de la Fase 3, y
    no se encontraron credenciales de prueba para ese rol sin recurrir a
    volcar variables de entorno/credenciales de un contenedor (descartado
    por seguridad). Revisar si se hace una pasada de hardening de Fase 3.
  - Sin commit/push — pendiente confirmación del usuario.
