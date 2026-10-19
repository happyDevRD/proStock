-- Solicitudes de reserva desde la web (iridiscencia.com). Ver documentacion/solicitudes-web.md.
-- Llegan sin sesión; el estudio las revisa y las convierte en orden o las descarta.

CREATE TABLE IF NOT EXISTS booking_requests (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(120)  NOT NULL,
    phone             VARCHAR(40)   NOT NULL,
    email             VARCHAR(160),
    event_type        VARCHAR(120),
    event_date        DATE,
    message           TEXT,
    status            VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    customer_id       BIGINT        REFERENCES customers(id) ON DELETE SET NULL,
    service_order_id  BIGINT        REFERENCES service_orders(id) ON DELETE SET NULL,
    client_ip         VARCHAR(64),
    handled_by        VARCHAR(100),
    handled_at        TIMESTAMP,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_booking_requests_status ON booking_requests (status, created_at DESC);
