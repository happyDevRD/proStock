-- Formulario de datos del cliente por WhatsApp (WhatsApp Flows).
-- El estudio lo envía desde la orden; el cliente lo llena dentro del chat y la respuesta llega
-- por el webhook con el mismo flow_token, que completa la ficha del cliente.
CREATE TABLE IF NOT EXISTS customer_data_requests (
    id                  BIGSERIAL PRIMARY KEY,
    -- Identifica la respuesta (flow_token); es secreto: quien lo tenga puede completar la ficha
    token               VARCHAR(64)  NOT NULL,
    customer_id         BIGINT       NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    service_order_id    BIGINT       REFERENCES service_orders(id) ON DELETE SET NULL,
    whatsapp_message_id BIGINT       REFERENCES whatsapp_messages(id) ON DELETE SET NULL,
    -- SENT (esperando respuesta) o COMPLETED
    status              VARCHAR(20)  NOT NULL,
    -- Lo que respondió el cliente (JSON tal cual) y qué se aplicó o se omitió
    response            TEXT,
    result_detail       VARCHAR(500),
    created_by          VARCHAR(100),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT uq_customer_data_requests_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_customer_data_requests_order ON customer_data_requests (service_order_id, created_at);
CREATE INDEX IF NOT EXISTS idx_customer_data_requests_customer ON customer_data_requests (customer_id, created_at);
