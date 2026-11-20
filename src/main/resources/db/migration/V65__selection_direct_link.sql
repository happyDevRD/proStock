-- Enlace directo del portal de selección: se registra cómo y desde qué dispositivo entró el cliente
-- (el estudio lo ve en la orden y recibe un aviso la primera vez).
ALTER TABLE service_order_selections ADD COLUMN IF NOT EXISTS last_login_method VARCHAR(20);
ALTER TABLE service_order_selections ADD COLUMN IF NOT EXISTS last_login_device VARCHAR(80);
