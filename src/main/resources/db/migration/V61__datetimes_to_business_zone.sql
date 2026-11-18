-- Fechas-hora sin zona: de hora UTC a hora local del negocio (America/Santo_Domingo, UTC-4).
--
-- Hasta esta versión el backend corría en UTC (contenedor sin TZ), así que todo lo que
-- generaba el servidor con LocalDateTime.now() o con DEFAULT now() quedó guardado en hora
-- UTC: 4 h adelantado respecto a RD. Desde esta versión la JVM corre en la zona del negocio
-- (APP_TIMEZONE), así que el historial se corrige restándole 4 h.
--
-- RD no tiene horario de verano, por lo que el desfase es exactamente 4 h en todo el historial.
--
-- NO se tocan:
--   * Columnas con zona (timestamptz): guardan un instante y se leen bien en cualquier zona.
--   * Horas que escribe el usuario en hora local (ya están bien):
--       service_orders.appointment_date / appointment_end
--       booking_requests.event_start / event_end
--       calendar_blocks.starts_at / ends_at
--       stock_movements.expiration_date
--   * Columnas DATE (sin hora).
--
-- Si se instala ProStock para un negocio en otra zona, esta migración solo afecta a datos que
-- ya existían cuando se aplicó (en una base nueva no hay filas y no hace nada).

DO $$
DECLARE
    target RECORD;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            ('acc_accounts', 'created_at'),
            ('acc_journal_entries', 'created_at'),
            ('acc_journal_entries', 'posted_at'),
            ('acc_sync_log', 'synced_at'),
            ('attendance_records', 'created_at'),
            ('audit_logs', 'created_at'),
            ('booking_requests', 'created_at'),
            ('booking_requests', 'handled_at'),
            ('booking_requests', 'updated_at'),
            ('calendar_blocks', 'created_at'),
            ('company_feature_config', 'updated_at'),
            ('credit_notes', 'created_at'),
            ('customers', 'portal_last_login'),
            ('employees', 'created_at'),
            ('employees', 'updated_at'),
            ('expenses', 'created_at'),
            ('expenses', 'updated_at'),
            ('locations', 'created_at'),
            ('purchase_orders', 'reception_date'),
            ('sale_payments', 'created_at'),
            ('sale_payments', 'payment_date'),
            ('sales', 'fecha_firma'),
            ('sales', 'sale_date'),
            ('service_order_items', 'created_at'),
            ('service_order_notes', 'created_at'),
            ('service_order_selections', 'created_at'),
            ('service_order_selections', 'expires_at'),
            ('service_order_selections', 'submitted_at'),
            ('service_order_selections', 'updated_at'),
            ('service_order_stages', 'entered_at'),
            ('service_orders', 'budget_approved_at'),
            ('service_orders', 'created_at'),
            ('service_orders', 'updated_at'),
            ('stock_movements', 'movement_date'),
            ('stock_transfers', 'completed_at'),
            ('stock_transfers', 'created_at'),
            ('supplier_payments', 'created_at'),
            ('supplier_payments', 'payment_date'),
            ('users', 'locked_until')
        ) AS t(table_name, column_name)
    LOOP
        -- Solo si la columna existe y sigue siendo "timestamp without time zone".
        IF EXISTS (
            SELECT 1 FROM information_schema.columns c
            WHERE c.table_schema = current_schema()
              AND c.table_name = target.table_name
              AND c.column_name = target.column_name
              AND c.data_type = 'timestamp without time zone'
        ) THEN
            EXECUTE format(
                'UPDATE %I SET %I = %I - interval ''4 hours'' WHERE %I IS NOT NULL',
                target.table_name, target.column_name, target.column_name, target.column_name
            );
        ELSE
            RAISE EXCEPTION 'V61: columna %.% no encontrada o con otro tipo', target.table_name, target.column_name;
        END IF;
    END LOOP;
END $$;
