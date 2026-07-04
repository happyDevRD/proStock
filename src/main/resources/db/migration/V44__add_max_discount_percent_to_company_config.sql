ALTER TABLE company_config
    ADD COLUMN IF NOT EXISTS max_discount_percent INTEGER NOT NULL DEFAULT 10;
