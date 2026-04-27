CREATE TABLE company_config (
    id BIGSERIAL PRIMARY KEY,
    rnc VARCHAR(11) NOT NULL UNIQUE,
    razon_social VARCHAR(255) NOT NULL,
    nombre_comercial VARCHAR(255),
    direccion VARCHAR(255) NOT NULL,
    municipio_codigo VARCHAR(6) NOT NULL,
    provincia_codigo VARCHAR(6) NOT NULL,
    actividad_economica VARCHAR(255) NOT NULL,
    numero_telefono VARCHAR(20) NOT NULL,
    correo_electronico VARCHAR(255) NOT NULL
);

ALTER TABLE company_config
    ADD CONSTRAINT chk_company_config_rnc_length CHECK (char_length(rnc) IN (9, 11));

ALTER TABLE company_config
    ADD CONSTRAINT chk_company_config_municipio_codigo_length CHECK (char_length(municipio_codigo) = 6);

ALTER TABLE company_config
    ADD CONSTRAINT chk_company_config_provincia_codigo_length CHECK (char_length(provincia_codigo) = 6);
