-- Orden de servicio creada a partir de una cotización (cotización → orden de servicio).
ALTER TABLE quotes ADD COLUMN converted_service_order_id BIGINT;
