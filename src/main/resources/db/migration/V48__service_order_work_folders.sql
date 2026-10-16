-- Carpetas de trabajo por orden de servicio (fotografía) y responsables asignados.
-- Ver documentacion/carpetas-ordenes.md.

ALTER TABLE service_orders
    ADD COLUMN IF NOT EXISTS photographer_id BIGINT REFERENCES employees(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS editor_id BIGINT REFERENCES employees(id) ON DELETE SET NULL,
    -- Ruta relativa a la raíz de carpetas (WORK_FOLDERS_ROOT), p. ej. "1 Agendadas/OS-2026-00123 Maria Perez"
    ADD COLUMN IF NOT EXISTS work_folder VARCHAR(500);
