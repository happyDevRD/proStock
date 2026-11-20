-- Sincronización de citas y bloqueos con Google Calendar (de ProStock a Calendar).
-- synced_at: la última vez que el evento quedó igual que la orden (se compara con updated_at);
-- attempt_at + error: último intento fallido, para reintentar con espera.
ALTER TABLE service_orders ADD COLUMN IF NOT EXISTS calendar_synced_at TIMESTAMP;
ALTER TABLE service_orders ADD COLUMN IF NOT EXISTS calendar_sync_attempt_at TIMESTAMP;
ALTER TABLE service_orders ADD COLUMN IF NOT EXISTS calendar_sync_error VARCHAR(500);
ALTER TABLE calendar_blocks ADD COLUMN IF NOT EXISTS calendar_synced_at TIMESTAMP;
ALTER TABLE calendar_blocks ADD COLUMN IF NOT EXISTS calendar_sync_attempt_at TIMESTAMP;
ALTER TABLE calendar_blocks ADD COLUMN IF NOT EXISTS calendar_sync_error VARCHAR(500);
