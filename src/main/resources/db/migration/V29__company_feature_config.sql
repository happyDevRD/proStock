-- V29: Catálogo de features configurables por instancia (Fase 1 - modularización)
-- Reemplaza gradualmente a company_module_config con un catálogo más granular
-- definido en código (FeatureCatalog). Solo se guarda una fila cuando el estado
-- difiere del defaultEnabled del catálogo.

CREATE TABLE IF NOT EXISTS company_feature_config (
    id BIGSERIAL PRIMARY KEY,
    feature_code VARCHAR(80) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100)
);

-- Migra el estado actual de company_module_config a los codes module.* del nuevo catálogo
INSERT INTO company_feature_config (feature_code, enabled, updated_at)
SELECT 'module.' || LOWER(module), enabled, NOW()
FROM company_module_config
ON CONFLICT (feature_code) DO NOTHING;
