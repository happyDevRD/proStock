-- Esquema inicial completo para despliegues con Flyway + JPA ddl-auto=validate (perfil prod).
-- Bases que ya aplicaron migraciones antiguas sin V0: CREATE IF NOT EXISTS evita fallos al inyectar V0 en el historial.

CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    contact_name VARCHAR(255),
    contact_email VARCHAR(255),
    phone VARCHAR(255),
    address VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone_number VARCHAR(255),
    address VARCHAR(255),
    rnc_cedula VARCHAR(11),
    tipo_identificacion VARCHAR(30),
    CONSTRAINT chk_customers_rnc_cedula_format CHECK (rnc_cedula IS NULL OR rnc_cedula ~ '^\d{9}(\d{2})?$'),
    CONSTRAINT chk_customers_tipo_identificacion CHECK (tipo_identificacion IS NULL OR tipo_identificacion IN ('RNC', 'CEDULA', 'PASAPORTE_EXTRANJERO'))
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'USER'
);

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    category_id BIGINT NOT NULL REFERENCES categories (id),
    supplier_id BIGINT NOT NULL REFERENCES suppliers (id),
    cost_price NUMERIC(19, 2) NOT NULL,
    selling_price NUMERIC(19, 2) NOT NULL,
    stock INTEGER NOT NULL,
    min_stock INTEGER NOT NULL,
    expiration_date DATE,
    for_sale BOOLEAN NOT NULL DEFAULT FALSE,
    indicador_facturacion INTEGER NOT NULL DEFAULT 4,
    tipo_bien_servicio INTEGER NOT NULL DEFAULT 1,
    unit_of_measure INTEGER NOT NULL DEFAULT 43,
    location VARCHAR(255),
    weight DOUBLE PRECISION,
    length DOUBLE PRECISION,
    width DOUBLE PRECISION,
    height DOUBLE PRECISION,
    status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    barcode VARCHAR(255) UNIQUE,
    tax_rate NUMERIC(19, 2),
    CONSTRAINT chk_products_indicador_facturacion CHECK (indicador_facturacion IN (1, 2, 3, 4)),
    CONSTRAINT chk_products_tipo_bien_servicio CHECK (tipo_bien_servicio IN (1, 2))
);

CREATE TABLE IF NOT EXISTS product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS sales (
    id BIGSERIAL PRIMARY KEY,
    sale_date TIMESTAMP NOT NULL,
    customer_id BIGINT REFERENCES customers (id),
    status VARCHAR(255) NOT NULL,
    tipo_comprobante VARCHAR(2),
    ncf VARCHAR(13) UNIQUE,
    monto_gravado_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    monto_exento NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_itbis NUMERIC(18, 2) NOT NULL DEFAULT 0,
    monto_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tipo_ingresos VARCHAR(2) NOT NULL DEFAULT '01',
    fecha_firma TIMESTAMP,
    codigo_seguridad VARCHAR(64),
    qr_payload_url VARCHAR(1000),
    qr_code_base64 TEXT,
    payment_method VARCHAR(20),
    CONSTRAINT chk_sales_tipo_comprobante_format CHECK (tipo_comprobante IS NULL OR tipo_comprobante ~ '^[0-9]{2}$'),
    CONSTRAINT chk_sales_tipo_ingresos CHECK (tipo_ingresos IN ('01'))
);

CREATE TABLE IF NOT EXISTS sale_items (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL REFERENCES sales (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(18, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES suppliers (id),
    order_date DATE NOT NULL,
    reception_date TIMESTAMP,
    status VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL,
    unit_price DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products (id),
    movement_date TIMESTAMP NOT NULL,
    quantity_change INTEGER NOT NULL,
    type VARCHAR(255) NOT NULL,
    reason VARCHAR(255),
    purchase_order_id BIGINT REFERENCES purchase_orders (id),
    sale_id BIGINT REFERENCES sales (id),
    user_id BIGINT REFERENCES users (id),
    source_location_id BIGINT,
    destination_location_id BIGINT,
    batch_number VARCHAR(255),
    expiration_date TIMESTAMP,
    unit_cost NUMERIC(19, 2),
    stock_before INTEGER,
    stock_after INTEGER
);

CREATE TABLE IF NOT EXISTS company_config (
    id BIGSERIAL PRIMARY KEY,
    rnc VARCHAR(11) NOT NULL UNIQUE,
    razon_social VARCHAR(255) NOT NULL,
    nombre_comercial VARCHAR(255),
    direccion VARCHAR(255) NOT NULL,
    municipio_codigo VARCHAR(6) NOT NULL,
    provincia_codigo VARCHAR(6) NOT NULL,
    actividad_economica VARCHAR(255) NOT NULL,
    numero_telefono VARCHAR(20) NOT NULL,
    correo_electronico VARCHAR(255) NOT NULL,
    logo_file_name VARCHAR(255),
    invoice_header_note TEXT,
    invoice_footer_text TEXT,
    CONSTRAINT chk_company_config_rnc_length CHECK (char_length(rnc) IN (9, 11)),
    CONSTRAINT chk_company_config_municipio_codigo_length CHECK (char_length(municipio_codigo) = 6),
    CONSTRAINT chk_company_config_provincia_codigo_length CHECK (char_length(provincia_codigo) = 6)
);

CREATE TABLE IF NOT EXISTS ncf_sequences (
    id BIGSERIAL PRIMARY KEY,
    tipo_comprobante VARCHAR(2) NOT NULL UNIQUE,
    prefijo VARCHAR(1) NOT NULL,
    valor_actual BIGINT NOT NULL,
    valor_final BIGINT NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    CONSTRAINT chk_ncf_sequence_tipo_format CHECK (tipo_comprobante ~ '^[0-9]{2}$'),
    CONSTRAINT chk_ncf_sequence_prefijo CHECK (prefijo IN ('B', 'E')),
    CONSTRAINT chk_ncf_sequence_valores CHECK (valor_actual >= 0 AND valor_final > 0 AND valor_actual < valor_final)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(100),
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at DESC);
