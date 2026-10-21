# Agenda de citas: duración, disponibilidad y bloqueo de fechas

Desde V55 (2026-10-21). Pedido de Iridiscencia: cada persona ve su agenda y las
reservas tienen inicio y duración (30 min, horas o día completo).

## Modelo

- `service_orders.appointment_date` = inicio; `appointment_end` = fin
  **exclusivo**; `appointment_all_day` = ocupa días completos (inicio a
  medianoche, fin a medianoche del día siguiente al último).
- Las reglas viven en `AppointmentWindow`: sin fin la cita dura **1 hora**; el
  fin debe ser posterior al inicio; máximo 14 días; con día completo el inicio
  se lleva a medianoche y el fin se redondea al final del día.
- Al editar, si llegan la misma fecha sin `appointmentEnd` ni
  `appointmentAllDay` (pantalla vieja), se conserva la duración guardada.
- Migración de datos: las citas existentes a medianoche (sin hora) quedaron como
  día completo; las que tenían hora, de 1 hora.
- `calendar_blocks`: fechas u horas sin agenda, para todo el estudio
  (`employee_id` NULL) o para una persona. Motivo obligatorio. Se registran en
  auditoría (`CALENDAR_BLOCK_CREATED/DELETED`).

## API

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/service-orders/calendar?from&to[&type][&staffId]` | Citas que se cruzan con el rango (máx. 62 días) |
| GET | `/api/service-orders/availability?start[&end][&allDay][&photographerId][&excludeOrderId]` | `conflicts` (el fotógrafo ya tiene cita o la fecha está bloqueada para el estudio o para él) y `sameTime` (otras sesiones a esa hora, informativo) |
| GET/POST | `/api/service-orders/blocks` | Listar (`from`, `to`) y crear bloqueos |
| DELETE | `/api/service-orders/blocks/{id}` | Quitar un bloqueo |
| GET | `/api/my-work/calendar?from&to` | Agenda personal (rol Producción): citas donde es fotógrafo o editor, también de órdenes cerradas, y bloqueos del estudio y propios |

Crear/editar una orden y convertir una solicitud web aceptan
`appointmentEnd`, `appointmentAllDay` (y al convertir, `photographerId`).

**Los choques solo avisan, no impiden guardar**: el estudio decide si agenda
igual (p. ej. dos fotógrafos en la misma boda). Solo choca el **fotógrafo**: el
editor no está en la sesión.

## Interfaz

- Formulario de la orden y conversión de solicitudes: fecha, hora y duración
  (30 min … 8 h, día completo) y aviso de disponibilidad en vivo.
- Órdenes → Calendario: vistas **Mes** y **Semana** (bloques por hora, carriles
  cuando se solapan, fila de día completo), solicitudes web punteadas, bloqueos
  rayados (clic para quitarlos) y botón **Bloquear fechas**. El filtro de
  persona muestra sus citas y los bloqueos del estudio y suyos.
- Mis trabajos → **Mi agenda** (semana por defecto); clic en una sesión abre su
  tarjeta con el paso que le toca.
- Componentes: `components/calendar/AppointmentCalendar.tsx` (genérico),
  `useCalendarRange.ts`, `service-orders/AppointmentFields.tsx`,
  `CalendarBlockModal.tsx`; utilidades en `lib/appointments.ts`.
