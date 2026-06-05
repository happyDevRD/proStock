-- V21: Emoji/icon field for products
ALTER TABLE products ADD COLUMN IF NOT EXISTS icon VARCHAR(20);
