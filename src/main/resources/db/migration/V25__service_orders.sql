-- V25: Service Orders module (customer journey tracking)

CREATE TABLE service_orders (
    id                  BIGSERIAL       PRIMARY KEY,
    order_number        VARCHAR(20)     NOT NULL UNIQUE,
    customer_id         BIGINT          REFERENCES customers(id) ON DELETE SET NULL,
    order_type          VARCHAR(20)     NOT NULL CHECK (order_type IN ('PHOTOGRAPHY','REPAIR','GENERAL')),
    title               VARCHAR(200)    NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'OPEN',
    current_stage       VARCHAR(50)     NOT NULL,
    appointment_date    TIMESTAMP,
    estimated_delivery  DATE,
    -- Budget / presupuesto
    estimated_amount    NUMERIC(18,2),
    budget_approved     BOOLEAN         NOT NULL DEFAULT FALSE,
    budget_approved_at  TIMESTAMP,
    deposit_amount      NUMERIC(18,2)   NOT NULL DEFAULT 0,
    -- Repair-specific device info
    device_brand        VARCHAR(100),
    device_model        VARCHAR(100),
    device_serial       VARCHAR(100),
    device_condition    VARCHAR(500),
    problem_description TEXT,
    -- General
    internal_notes      TEXT,
    created_by          VARCHAR(100),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE service_order_stages (
    id                  BIGSERIAL   PRIMARY KEY,
    service_order_id    BIGINT      NOT NULL REFERENCES service_orders(id) ON DELETE CASCADE,
    stage_name          VARCHAR(50) NOT NULL,
    entered_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    notes               VARCHAR(500),
    actor               VARCHAR(100)
);

CREATE TABLE service_order_notes (
    id                  BIGSERIAL   PRIMARY KEY,
    service_order_id    BIGINT      NOT NULL REFERENCES service_orders(id) ON DELETE CASCADE,
    content             TEXT        NOT NULL,
    author              VARCHAR(100),
    is_internal         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_service_orders_customer  ON service_orders(customer_id);
CREATE INDEX idx_service_orders_status    ON service_orders(status);
CREATE INDEX idx_service_orders_type      ON service_orders(order_type);
CREATE INDEX idx_so_stages_order_id       ON service_order_stages(service_order_id);
CREATE INDEX idx_so_notes_order_id        ON service_order_notes(service_order_id);

-- Register the module as enabled by default
INSERT INTO company_module_config (module, enabled) VALUES ('SERVICE_ORDERS', FALSE)
ON CONFLICT (module) DO NOTHING;
