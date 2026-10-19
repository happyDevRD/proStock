# Carpetas de trabajo por orden de servicio

Cada orden de **fotografía** tiene una carpeta en el NAS donde se guarda todo su material. ProStock la crea sola al crear la orden y mantiene un `INFO.txt` con los datos de la orden.

## Estructura

```
Ordenes/                                ← carpeta compartida de DSM (raíz)
├── 1 Agendadas/
├── 2 En sesion/
├── 3 En seleccion/
├── 4 En edicion/
├── 5 Listas para entregar/
├── 6 Entregadas/
└── 7 Canceladas/
      └── OS-2026-00123 Maria Perez/
            ├── INFO.txt      ← cliente, teléfono, evento, fecha, fotógrafo, editor, servicios
            ├── 1 Camara/     ← material tal como sale de la cámara
            ├── 2 Seleccion/  ← previas en JPG para que el cliente elija
            ├── 3 Edicion/    ← lo que eligió el cliente, para editar
            └── 4 Final/      ← lo que se entrega
```

- El nombre de la carpeta lleva el número de orden y el nombre del cliente, **nunca el teléfono**. Se quitan los caracteres que Windows no acepta.
- `INFO.txt` se reescribe cada vez que cambia la orden (datos, responsables, servicios). No se edita a mano.
- Una sola carpeta compartida a propósito: en Synology cada carpeta compartida es un subvolumen, y mover entre subvolúmenes copia y borra (lento, ocupa el doble). Dentro de `Ordenes`, cambiar de etapa es un renombrado instantáneo.

## Fases

| Fase | Estado | Qué hace |
|---|---|---|
| 1 | Hecha | Carpeta e `INFO.txt` al crear la orden; fotógrafo y editor asignables; ruta visible en el detalle; botón "Crear carpeta" para órdenes viejas o si falló |
| 2 | Hecha | Al avanzar de etapa (o cancelar/entregar), la carpeta se mueve a la carpeta de la etapa nueva |
| 3 | Hecha | Portal web de selección para el cliente (en producción desde 2026-10-16) |

## Movimiento entre etapas (fase 2)

- Cada cambio de la orden deja la carpeta en la carpeta de su etapa actual (`7 Canceladas` si se canceló). Es un renombrado dentro del mismo volumen: instantáneo y sin copiar.
- Si alguien la movió a mano, se busca por número de orden (`OS-AAAA-NNNNN …`) en todas las carpetas de etapa antes de crear otra.
- Si en el destino ya hay una carpeta con el mismo nombre, se usa `… (2)`; nunca se sobrescribe.
- Si no se puede mover (normalmente archivos abiertos por SMB), la carpeta se queda donde estaba, se registra en el log y el detalle de la orden muestra un aviso con el botón "Mover carpeta". También se reintenta sola en el siguiente cambio de la orden.
- Conviene cerrar los archivos de la carpeta antes de avanzar la etapa: un programa que tenga un archivo abierto (Photoshop, Lightroom) puede fallar al guardar después del movimiento.

## Cómo funciona

- `WorkFolderService` escucha `ServiceOrderChangedEvent` **después del commit** (`@TransactionalEventListener`). Un error de disco se registra en el log y no afecta la orden. Se puede reintentar con `POST /api/service-orders/{id}/work-folder`.
- Solo aplica a órdenes `PHOTOGRAPHY`. Si `WORK_FOLDERS_ROOT` está vacío o no existe, la función queda apagada y la interfaz no muestra nada nuevo (otras instalaciones no se ven afectadas).
- `GET /api/service-orders/staff` lista empleados activos (id, nombre, cargo) para asignar fotógrafo y editor, sin exponer datos de nómina.
- Migración `V48`: `service_orders.photographer_id`, `editor_id` (FK a `employees`) y `work_folder` (ruta relativa a la raíz).

## Configuración

| Variable | Ejemplo | Uso |
|---|---|---|
| `WORK_FOLDERS_ROOT` | `/data/ordenes` | Raíz dentro del contenedor. Vacía = desactivado |
| `WORK_FOLDERS_DISPLAY_PREFIX` | `\\ESTUDIO-NAS-01\Ordenes` | Cómo ve la raíz el personal; se antepone a la ruta en la interfaz |

En el NAS de Iridiscencia el compose monta `/volume1/Ordenes:/data/ordenes`. El backend corre como `spring` (uid 100, gid 101), y el gid 101 del NAS es el grupo `administrators`, así que la carpeta compartida debe dar lectura/escritura a `administrators`.

## Fotógrafos y editores: rol PRODUCCION y "Mis trabajos"

- **Funciones del empleado**: casillas Fotógrafo y Editor (puede tener ambas). En la orden, cada lista muestra a quienes tienen esa función, más el ya asignado. "+ Nuevo" crea el empleado con solo nombre y funciones (`POST /api/service-orders/staff`, requiere `employees.create`).
- **Empleado ↔ usuario**: `employees.user_id` (único). Se elige en el formulario del empleado ("Usuario del sistema"); `GET /api/employees/linkable-users` lista los usuarios e indica los ya vinculados.
- **Rol PRODUCCION**: solo tiene `view.my_work`. Entra directo a "Mis trabajos" (`/api/my-work`): sus órdenes abiertas como fotógrafo o editor, sin montos ni cobros, con la carpeta del NAS y notas.
- **Pasos permitidos** (validados en `MyWorkService`, no en la interfaz):

| Etapa actual | Quién | Acción | Etapa nueva |
|---|---|---|---|
| Agendada | Fotógrafo | Sesión realizada | Sesión |
| Sesión | Fotógrafo | Fotos cargadas: pasar a selección | Selección |
| Edición | Editor | Edición terminada | Lista |

  Selección → Edición (cliente) y la entrega (cobro del saldo) siguen en manos de administración. Una orden ajena responde 404, como si no existiera.
- **Lista blanca de API** (`ProductionRoleAccessFilter`): el rol PRODUCCION solo llega a `/api/auth/**`, `/api/my-work/**` y lecturas de `features`, `modules/flags`, `company-config` y `portal/company-info`. Hace falta porque muchos endpoints de lectura solo exigen estar autenticado. El frontend no pide datos de negocio para este rol.
- Migración `V49`: `employees.es_fotografo`, `es_editor`, `user_id`; permiso `view.my_work` para GESTOR, ADMIN, MANAGER y PRODUCCION.

## Material en la carpeta, filtros y avisos

- **Contadores**: el detalle de la orden y "Mis trabajos" muestran cuántos archivos hay en cada subcarpeta (incluye subcarpetas como `CAMARA UNO`; ignora `.DS_Store`, `Thumbs.db`, `@eaDir`, `#recycle` y ocultos).
- **Validación de material** (solo en "Mis trabajos"): "Fotos cargadas" exige archivos en `1 Camara` y "Edición terminada" exige archivos en `4 Final`. Administración puede avanzar sin esa validación desde el detalle de la orden.
- **Filtro por responsable** en el tablero (`GET /paginated?staffId=`): órdenes donde esa persona es fotógrafo o editor. Las tarjetas muestran fotógrafo y editor.
- **WhatsApp al cliente**: el detalle de la orden ofrece un enlace `wa.me` con el mensaje ya escrito según la etapa (Selección: fotos listas para elegir; Lista: fotos listas para entregar, con el saldo pendiente si hay). Los números de RD de 10 dígitos se completan con el 1.
- **Salarios**: el backend ya no devuelve `salarioBase`, `tipoSalario` ni `comisionPorcentaje` a quien no tiene `employees.view_salary`, y al guardar no los modifica.

## Fase 3: el cliente elige sus fotos en la web

1. El fotógrafo exporta previas en JPG (idealmente con marca de agua) a `2 Seleccion` y marca "Fotos cargadas"; la orden queda en Selección.
2. En el detalle de la orden, "Selección del cliente en la web": cupo sugerido según los combos (primer "N fotos" de la descripción × cantidad), vigencia (15 días por defecto) → **Generar enlace** → copiar o **Enviar enlace por WhatsApp**.
3. El cliente abre `https://iridiscencia.com/seleccion/<token>`, marca sus fotos (se guardan solas como borrador), las ve en grande y confirma. Si elige más que el cupo, ve el costo y debe aceptarlo.
4. Al confirmar: las fotos extra se agregan a la orden con `ADI-FOTO-DIG` (precio base, sin duplicar ITBIS); los originales de `1 Camara` con el mismo nombre se enlazan (hardlink) en `3 Edicion` junto con `SELECCION.txt`; la orden pasa a Edición y la carpeta se mueve a `4 En edicion`.

Técnico:
- Tabla `service_order_selections` (V50): un enlace por orden; regenerarlo cambia el token y conserva lo marcado.
- API pública `/api/public/selection/{token}` (permitAll; el acceso es el token de 32 bytes). Los nombres de archivo se validan contra el listado de `2 Seleccion` (sin rutas ni ocultos).
- Miniaturas (480 px) y vista (1600 px) en `2 Seleccion/.miniaturas` y `.vista` (ocultas: no cuentan como material), generadas de a una con submuestreo por la poca memoria del NAS.
- Exposición: el nginx de `iridiscencia-web` reenvía solo `/api/seleccion/` al backend, que está en la red `iridiscencia-web-net`. Ninguna otra ruta de ProStock llega a internet.
- Variables: `SELECTION_PUBLIC_URL` (base de los enlaces), `SELECTION_EXTRA_SKU` (por defecto `ADI-FOTO-DIG`).

### Mejoras del portal (fase 3 completa)

- **Marca de agua**: miniaturas y vista ampliada llevan el nombre del estudio repetido en diagonal (tenue). `SELECTION_WATERMARK` cambia el texto; `off` la quita. Las versiones con marca se guardan en carpetas ocultas que dependen del texto (si cambia, se regeneran). Si no se puede generar, el portal no muestra la foto: nunca entrega la previa original. Requiere fuentes en la imagen (`ttf-dejavu` en el Dockerfile).
- **Previas reemplazadas**: si el fotógrafo reexporta una previa, su miniatura se regenera.
- **Seguimiento en la orden**: fecha del enlace, última actividad del cliente, lista de fotos marcadas/elegidas y botón "Recordar por WhatsApp" (a partir de 2 días sin confirmar).
- **Aviso al estudio** al confirmar: nota en la orden y, si están `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID` (el mismo bot del monitor), mensaje por Telegram.
