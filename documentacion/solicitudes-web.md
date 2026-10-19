# Solicitudes de reserva desde la web

El formulario de contacto de iridiscencia.com registra cada solicitud en ProStock antes de abrir WhatsApp. El estudio la ve en **Órdenes de servicio → Solicitudes web** y la convierte en orden o la descarta.

## Flujo

1. El cliente llena el formulario (nombre, teléfono, correo, tipo de evento, fecha, mensaje) y pulsa "Enviar por WhatsApp".
2. La web hace `POST /api/solicitudes` y recibe la referencia (`S-0012`). Después abre WhatsApp con el mensaje redactado, que termina en `Solicitud S-0012`. Si ProStock no responde en 5 s, WhatsApp se abre igual, sin referencia: la reserva no se pierde.
3. Llega un aviso por Telegram (mismo bot del monitor) y el tablero de órdenes muestra "N solicitudes de reserva desde la web por atender".
4. En la bandeja, cada solicitud muestra:
   - si ya existe un cliente con ese correo o teléfono (últimos 10 dígitos, sin importar el formato);
   - las órdenes ya agendadas ese día (para ver la disponibilidad);
   - "Responder por WhatsApp" con un mensaje que cita la referencia.
5. **Convertir en orden**: título, fecha y hora → crea la orden en su etapa inicial (Agendada en fotografía) con el cliente existente o uno nuevo, y las notas internas con la referencia, el tipo, la fecha pedida y el mensaje. Se crea su carpeta en el NAS como cualquier orden. Los servicios, el fotógrafo y el anticipo se agregan después en la orden.
6. **Descartar** (spam, cliente que no siguió). Se puede volver a pendientes desde "Atendidas".

## Protección

- Solo se expone `POST /api/solicitudes` (nginx de iridiscencia-web → `/api/public/booking-requests`); el resto de la bandeja exige sesión y permiso `view.service_orders`, como las órdenes.
- Campo trampa `botcheck`: si viene marcado se responde como si todo fuera bien, sin guardar.
- Límites: 5 solicitudes por IP por hora (IP real en `CF-Connecting-IP`, vía Cloudflare) y 100 en total por día → 429.
- Mismo teléfono, tipo y fecha en la última hora → devuelve la misma referencia (doble clic).
- Si el módulo de órdenes está apagado, el endpoint público responde 403.

## Técnico

- Tabla `booking_requests` (V51). Estados: `NEW`, `CONVERTED`, `DISCARDED`. La referencia sale del id (`S-%04d`).
- `BookingRequestService` (lógica y límites), `PublicBookingRequestController` (público) y `BookingRequestController` (`/api/booking-requests`: listar, contar, `convert`, `discard`, `reopen`).
- Frontend: `BookingRequestsPanel` dentro de `ServiceOrdersView`; el contador se refresca cada minuto.
- Siguiente fase (no hecha): WhatsApp Cloud API vía ConectorIA para recibir también los mensajes que no pasan por la web y enviar confirmaciones automáticas.
