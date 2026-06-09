-- V27: Add service_order_type to company_config
-- Defines the default order type used when creating new service orders.
ALTER TABLE company_config
    ADD COLUMN IF NOT EXISTS service_order_type VARCHAR(20) NOT NULL DEFAULT 'PHOTOGRAPHY';
