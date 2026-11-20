-- Enlace directo del portal de selección: se registra cómo y desde qué dispositivo entró el cliente
-- (el estudio lo ve en la orden y recibe un aviso la primera vez).
ALTER TABLE service_order_selections ADD COLUMN IF NOT EXISTS last_login_method VARCHAR(20);
ALTER TABLE service_order_selections ADD COLUMN IF NOT EXISTS last_login_device VARCHAR(80);

-- Bandeja de WhatsApp: el estudio puede guardar (destacar), archivar o borrar mensajes entrantes.
ALTER TABLE whatsapp_inbound_messages ADD COLUMN IF NOT EXISTS saved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE whatsapp_inbound_messages ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_whatsapp_inbound_archived ON whatsapp_inbound_messages (archived_at, sent_at DESC);
