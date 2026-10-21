-- Solicitudes web con hora y duración: ocupan la agenda mientras están pendientes.
-- Ver documentacion/agenda-citas.md (sección «Reservas desde la web»).
ALTER TABLE booking_requests ADD COLUMN IF NOT EXISTS event_start TIMESTAMP;
ALTER TABLE booking_requests ADD COLUMN IF NOT EXISTS event_end TIMESTAMP;
ALTER TABLE booking_requests ADD COLUMN IF NOT EXISTS event_all_day BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_booking_requests_event ON booking_requests (status, event_start, event_end);
