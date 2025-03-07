ALTER TABLE product_images
    ADD COLUMN file_name VARCHAR(255) NOT NULL DEFAULT 'default.jpg';