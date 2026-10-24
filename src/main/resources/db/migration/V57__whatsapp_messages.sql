-- WhatsApp Cloud API, Fase A: avisos automáticos con plantillas y consentimiento.
-- Ver documentacion/whatsapp-fase-a.md.

-- Consentimiento del cliente para recibir avisos por WhatsApp (NULL = no se ha preguntado).
ALTER TABLE customers ADD COLUMN IF NOT EXISTS whatsapp_opt_in BOOLEAN;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS whatsapp_opt_in_at TIMESTAMPTZ;
-- 'web' (formulario de iridiscencia.com) o 'staff' (lo marcó el estudio)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS whatsapp_opt_in_source VARCHAR(20);

ALTER TABLE booking_requests ADD COLUMN IF NOT EXISTS whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE;

-- Registro de cada aviso: se guarda antes de enviarse (bandeja de salida) y conserva su resultado.
CREATE TABLE IF NOT EXISTS whatsapp_messages (
    id                 BIGSERIAL PRIMARY KEY,
    kind               VARCHAR(40)  NOT NULL,
    template_name      VARCHAR(100) NOT NULL,
    language           VARCHAR(10)  NOT NULL,
    recipient_phone    VARCHAR(20),
    recipient_name     VARCHAR(160),
    -- Variables del cuerpo en orden, como arreglo JSON de textos
    body_params        TEXT         NOT NULL,
    -- Sufijo del botón de URL dinámico (p. ej. el token del portal de selección)
    button_param       VARCHAR(200),
    customer_id        BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    service_order_id   BIGINT REFERENCES service_orders(id) ON DELETE SET NULL,
    booking_request_id BIGINT REFERENCES booking_requests(id) ON DELETE SET NULL,
    -- Evita avisos repetidos: un mismo hecho genera siempre la misma clave
    dedupe_key         VARCHAR(200) NOT NULL,
    -- PENDING, SENDING, SENT, SIMULATED, FAILED, SKIPPED
    status             VARCHAR(20)  NOT NULL,
    status_detail      VARCHAR(500),
    wamid              VARCHAR(128),
    attempts           INT          NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ,
    created_by         VARCHAR(100),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at            TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_whatsapp_messages_dedupe UNIQUE (dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_messages_outbox ON whatsapp_messages (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_whatsapp_messages_order ON whatsapp_messages (service_order_id, created_at);
CREATE INDEX IF NOT EXISTS idx_whatsapp_messages_customer ON whatsapp_messages (customer_id, created_at);

-- Estados de entrega que llegan por el webhook (sent → delivered → read, o failed).
ALTER TABLE whatsapp_messages ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;
ALTER TABLE whatsapp_messages ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
CREATE UNIQUE INDEX IF NOT EXISTS uq_whatsapp_messages_wamid ON whatsapp_messages (wamid) WHERE wamid IS NOT NULL;

-- Mensajes que los clientes escriben al número de la API (llegan por el webhook).
CREATE TABLE IF NOT EXISTS whatsapp_inbound_messages (
    id             BIGSERIAL PRIMARY KEY,
    -- Id de Meta: el webhook puede repetir un evento, así se guarda una sola vez
    wamid          VARCHAR(128) NOT NULL,
    from_phone     VARCHAR(20)  NOT NULL,
    profile_name   VARCHAR(160),
    -- text, image, audio, video, document, sticker, location, button, interactive, reaction…
    type           VARCHAR(30)  NOT NULL,
    body           TEXT,
    media_id       VARCHAR(128),
    media_mime     VARCHAR(100),
    media_filename VARCHAR(255),
    -- Mensaje nuestro al que responde (si el cliente usó "responder")
    context_wamid  VARCHAR(128),
    customer_id    BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    sent_at        TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    auto_replied   BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Cuándo lo vio el estudio en ProStock (null = sin leer)
    seen_at        TIMESTAMPTZ,
    raw            TEXT,
    CONSTRAINT uq_whatsapp_inbound_wamid UNIQUE (wamid)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_inbound_phone ON whatsapp_inbound_messages (from_phone, sent_at);
CREATE INDEX IF NOT EXISTS idx_whatsapp_inbound_unseen ON whatsapp_inbound_messages (seen_at, id);
