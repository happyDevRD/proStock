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
- **Fase 3 (Menú y navegación): ✅ implementada en frontend, pendiente commit/push**
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
- **Próximo paso:** commitear y pushear Fase 3 (frontend) cuando el usuario
  confirme, luego decidir si seguimos con Fase 4 (Dashboard) o Fase 5
  (Empleados/RRHH).
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
- `Dashboard.tsx` — widgets reordenables por drag & drop (dnd-kit),
  persistidos en `localStorage`.
- KPIs actuales: ventas, ingresos, ticket promedio, borradores, stock, CxC.
- Bloques de contenido: gráfico de ventas, accesos directos, ventas
  recientes, estado, stock crítico.

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

### Fase 3 — Menú y navegación ✅ (frontend implementado, pendiente push)
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
- [ ] Nuevos KPIs: flujo de caja, antigüedad de CxC/CxP, top
      productos/clientes, comparación vs período anterior.
- [ ] Filtros de rango de fechas.
- [ ] Layouts por defecto según rol (cajero ve ventas, admin ve financiero).
- [ ] Exportar/imprimir resumen del dashboard.

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
  roles `admin` y `cashier`. Pendiente: commit + push (frontend).
