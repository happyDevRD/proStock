DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'company_config' AND column_name = 'logo_file_name'
    ) THEN
        ALTER TABLE company_config ADD COLUMN logo_file_name VARCHAR(255);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'company_config' AND column_name = 'invoice_header_note'
    ) THEN
        ALTER TABLE company_config ADD COLUMN invoice_header_note TEXT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'company_config' AND column_name = 'invoice_footer_text'
    ) THEN
        ALTER TABLE company_config ADD COLUMN invoice_footer_text TEXT;
    END IF;
END $$;
