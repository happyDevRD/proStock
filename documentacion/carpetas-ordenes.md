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
└── 6 Entregadas/
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
| 2 | Pendiente | Al avanzar de etapa, la carpeta se mueve a la carpeta de la etapa nueva |
| 3 | Pendiente | Portal web de selección para el cliente |

Hasta la fase 2, la carpeta se queda en la etapa en que se creó (normalmente `1 Agendadas`).

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
