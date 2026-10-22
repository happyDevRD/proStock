# WhatsApp Cloud API — plantillas de Iridiscencia (Fase A)

> Aprobadas por el usuario el 2026-10-22. **Aún no enviadas a Meta**: la cuenta de WhatsApp donde vive el +1 849-631-8221 es una cuenta de la app WhatsApp Business (no de Cloud API) y Meta no permite crear plantillas en ella por la API (`SMB_ACCOUNT_NOT_ON_COEX`). Se crearán en la cuenta nueva que se genere al dar de alta el número nuevo solo para la API.

Comunes a todas: categoría **UTILITY**, idioma `es`, pie «Iridiscencia Film», botón URL **«Escribir al estudio»** → `https://wa.me/18496318221`. Parámetros posicionales; los ejemplos son inventados (solo para la revisión de Meta).

| Plantilla | Disparador en ProStock | Cuerpo | Variables (ejemplo) |
|---|---|---|---|
| `solicitud_recibida` | Reserva desde iridiscencia.com | Hola {{1}}, recibimos tu solicitud de {{2}} para {{3}}. Te escribiremos pronto para confirmar los detalles de tu sesión. | nombre (Ana) · paquete (Sesión de embarazo) · fecha y hora (el sábado 15 de noviembre a las 10:00 a. m.) |
| `cita_confirmada` | Solicitud convertida en orden / cita agendada | Hola {{1}}, tu sesión de {{2}} quedó confirmada para {{3}}. Lugar: {{4}}. ¡Te esperamos! | nombre · paquete · fecha y hora · lugar (por defecto: Plaza Dora, 2do nivel, calle Porvenir, San Pedro de Macorís) |
| `recordatorio_cita` | Automático 24 h antes | Hola {{1}}, te recordamos tu sesión de {{2}}, programada para {{3}}. Lugar: {{4}}. Si necesitas cambiar algo, escríbenos al WhatsApp del estudio. | igual que la anterior (fecha: «mañana sábado 15 de noviembre a las 10:00 a. m.») |
| `fotos_listas_seleccion` | Orden pasa a selección | Hola {{1}}, ¡las fotos de tu sesión de {{2}} ya están listas! Toca el botón para elegir tus favoritas desde tu celular. | nombre · paquete. **Botón extra** (primero): URL «Elegir mis fotos» → `https://iridiscencia.com/seleccion/{{1}}` (token del portal, ej. `k3f9x2ab`) |
| `trabajo_listo` | Orden pasa a entrega, sin saldo | Hola {{1}}, ¡tu trabajo de la orden {{2}} ya está listo! Escríbenos para coordinar la entrega. | nombre · nº de orden (OS-0042) |
| `trabajo_listo_con_saldo` | Orden pasa a entrega, con saldo | Hola {{1}}, ¡tu trabajo de la orden {{2}} ya está listo! Queda un saldo pendiente de {{3}}. Escríbenos para coordinar la entrega. | nombre · nº de orden · saldo (RD$2,500.00) |
| `pago_recibido` | Se registra un pago/anticipo | Hola {{1}}, recibimos tu pago de {{2}} para la orden {{3}}. Saldo pendiente: {{4}}. ¡Gracias por confiar en nosotros! | nombre · monto (RD$3,000.00) · nº de orden · saldo |
| `recordatorio_saldo` | Cobro manual o programado | Hola {{1}}, te recordamos que la orden {{2}} tiene un saldo pendiente de {{3}}. Si ya realizaste el pago, por favor ignora este mensaje. | nombre · nº de orden · saldo |

Fase B (con webhook): versión de `recordatorio_cita` con botones de respuesta rápida «Confirmo» / «Necesito cambiar».
