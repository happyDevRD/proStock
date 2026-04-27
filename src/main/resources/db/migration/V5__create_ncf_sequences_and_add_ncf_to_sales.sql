CREATE TABLE ncf_sequences (
    id BIGSERIAL PRIMARY KEY,
    tipo_comprobante VARCHAR(2) NOT NULL UNIQUE,
    prefijo VARCHAR(1) NOT NULL,
    valor_actual BIGINT NOT NULL,
    valor_final BIGINT NOT NULL,
    fecha_vencimiento DATE NOT NULL
);

ALTER TABLE ncf_sequences
    ADD CONSTRAINT chk_ncf_sequence_tipo_format CHECK (tipo_comprobante ~ '^[0-9]{2}$');

ALTER TABLE ncf_sequences
    ADD CONSTRAINT chk_ncf_sequence_prefijo CHECK (prefijo IN ('B', 'E'));

ALTER TABLE ncf_sequences
    ADD CONSTRAINT chk_ncf_sequence_valores CHECK (valor_actual >= 0 AND valor_final > 0 AND valor_actual < valor_final);

ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS tipo_comprobante VARCHAR(2);

ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS ncf VARCHAR(13);

ALTER TABLE sales
    ADD CONSTRAINT uk_sales_ncf UNIQUE (ncf);

ALTER TABLE sales
    ADD CONSTRAINT chk_sales_tipo_comprobante_format CHECK (tipo_comprobante IS NULL OR tipo_comprobante ~ '^[0-9]{2}$');
