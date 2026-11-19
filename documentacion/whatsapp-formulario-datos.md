# Formulario de datos del cliente por WhatsApp (WhatsApp Flows)

El estudio agenda con solo nombre y teléfono; desde la orden pulsa **Pedir datos** y el cliente
recibe, desde el número de avisos (+1 829-546-8012), un mensaje con el botón «Completar mis datos».
El formulario se abre dentro del chat; al enviarlo, la respuesta llega al webhook y completa la ficha.

## Piezas

| Pieza | Dónde |
|---|---|
| Formulario (Flow JSON 7.3, estático, sin endpoint) | `documentacion/whatsapp-flow-datos-cliente.json` — Flow `registro_cliente`, id **1714037416720625**, cuenta 1859146558594612 |
| Plantilla | `datos_cliente` (abajo) |
| Solicitudes | tabla `customer_data_requests` (V63): token = `flow_token` |
| Envío | `POST /api/service-orders/{id}/whatsapp/data-requests` → `CustomerDataRequestService.request` → bandeja (`WhatsAppOutbox`, tipo `CUSTOMER_DATA_REQUEST`, botón `sub_type: flow` con nombre/apellido precargados) |
| Respuesta | webhook `interactive.nfm_reply.response_json` → `CustomerDataRequestService.complete` → ficha del cliente + aviso al estudio (Telegram) + «¡Gracias…!» al cliente |
| Interruptor | `whatsapp.customer_data` (apagado por defecto; depende de `module.whatsapp`) |

## Reglas al aplicar la respuesta

- Campos vacíos no borran lo que ya había.
- Correo: se omite si es inválido o pertenece a otro cliente (UNIQUE).
- Cédula/RNC: solo 11 o 9 dígitos; el tipo se deduce (11 = cédula, 9 = RNC).
- La casilla de avisos marca `whatsapp_opt_in = true` con origen `whatsapp`; desmarcada no cambia nada.
- Respuesta repetida o token desconocido: no hace nada.
- El formulario se envía aunque el cliente no haya aceptado avisos: lo pide él al escribir al estudio.

## Plantilla `datos_cliente` (UTILITY, es)

- Cuerpo: `Hola {{1}}, para preparar tu sesión en Iridiscencia necesitamos algunos datos. Toca el botón para completarlos aquí mismo, toma menos de un minuto.`
  - Ejemplo de {{1}}: `Ana`
- Botón FLOW: texto `Completar mis datos`, `flow_id` 1714037416720625, `flow_action` navigate, `navigate_screen` DATOS.

## Requisitos de Meta (2026-11-18)

Publicar el Flow falló con «Blocked by Integrity»: el negocio **IRIDISCENCIA_FILM (552722489212393) no
ha pasado la verificación de negocio** (health_status 141010). Pasos para activarlo:

1. Verificar el negocio en Meta Business Suite (Centro de seguridad).
2. Publicar el Flow: `POST /1714037416720625/publish`.
3. Crear la plantilla `datos_cliente` con el botón FLOW y esperar aprobación.
4. Encender `whatsapp.customer_data` en el Centro de Módulos.

**2026-11-19:** negocio verificado; Flow **publicado**; plantilla `datos_cliente` creada (id 1804446534100774) y en revisión. Falta solo el paso 4 cuando Meta la apruebe.

Mientras tanto el código está desplegable: con el interruptor apagado el botón no aparece.
