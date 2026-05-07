ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20);
UPDATE users SET role = 'USER' WHERE role IS NULL;
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE users ALTER COLUMN role SET NOT NULL;

UPDATE users SET role = 'ADMIN' WHERE username = 'admin';
UPDATE users SET role = 'MANAGER' WHERE username = 'manager';
UPDATE users SET role = 'CASHIER' WHERE username = 'cashier';
UPDATE users SET role = 'USER' WHERE username = 'user';
