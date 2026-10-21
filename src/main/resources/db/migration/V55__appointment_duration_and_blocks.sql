-- Citas con duración y bloqueo de fechas. Ver documentacion/agenda-citas.md.

-- Fin de la cita (exclusivo) y si ocupa el día completo.
ALTER TABLE service_orders ADD COLUMN IF NOT EXISTS appointment_end TIMESTAMP;
ALTER TABLE service_orders ADD COLUMN IF NOT EXISTS appointment_all_day BOOLEAN NOT NULL DEFAULT FALSE;

-- Citas anteriores: a medianoche no tenían hora (día completo); con hora, 1 hora.
UPDATE service_orders
   SET appointment_all_day = TRUE,
       appointment_end = appointment_date + INTERVAL '1 day'
 WHERE appointment_date IS NOT NULL
   AND appointment_end IS NULL
   AND appointment_date = date_trunc('day', appointment_date);

UPDATE service_orders
   SET appointment_end = appointment_date + INTERVAL '1 hour'
 WHERE appointment_date IS NOT NULL
   AND appointment_end IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_orders_appointment
    ON service_orders (appointment_date, appointment_end);

-- Fechas u horas en que no se agenda: todo el estudio (employee_id NULL) o una persona.
CREATE TABLE IF NOT EXISTS calendar_blocks (
    id           BIGSERIAL PRIMARY KEY,
    starts_at    TIMESTAMP    NOT NULL,
    ends_at      TIMESTAMP    NOT NULL,
    all_day      BOOLEAN      NOT NULL DEFAULT FALSE,
    employee_id  BIGINT       REFERENCES employees(id) ON DELETE CASCADE,
    reason       VARCHAR(200) NOT NULL,
    created_by   VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_calendar_blocks_range CHECK (ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_calendar_blocks_range ON calendar_blocks (starts_at, ends_at);
