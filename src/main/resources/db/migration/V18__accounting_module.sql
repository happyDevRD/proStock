-- ============================================================
-- V18 - Accounting Module
-- ============================================================

-- -------------------------------------------------------
-- acc_accounts : chart of accounts
-- -------------------------------------------------------
CREATE TABLE acc_accounts (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(20)  UNIQUE NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    type                  VARCHAR(20)  NOT NULL,   -- ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE
    nature                VARCHAR(10)  NOT NULL,   -- DEBIT/CREDIT
    parent_id             BIGINT       REFERENCES acc_accounts(id),
    allows_transactions   BOOLEAN      DEFAULT TRUE,
    is_system             BOOLEAN      DEFAULT FALSE,
    active                BOOLEAN      DEFAULT TRUE,
    created_at            TIMESTAMP    DEFAULT NOW()
);

-- -------------------------------------------------------
-- acc_journal_entries
-- -------------------------------------------------------
CREATE TABLE acc_journal_entries (
    id           BIGSERIAL PRIMARY KEY,
    entry_date   DATE         NOT NULL,
    reference    VARCHAR(100),
    description  TEXT         NOT NULL,
    entry_type   VARCHAR(30)  NOT NULL DEFAULT 'MANUAL',   -- MANUAL/AUTO_SALE/AUTO_PURCHASE
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',    -- DRAFT/POSTED
    created_at   TIMESTAMP    DEFAULT NOW(),
    created_by   VARCHAR(100),
    posted_at    TIMESTAMP,
    posted_by    VARCHAR(100)
);

-- -------------------------------------------------------
-- acc_journal_entry_lines
-- -------------------------------------------------------
CREATE TABLE acc_journal_entry_lines (
    id               BIGSERIAL PRIMARY KEY,
    journal_entry_id BIGINT        NOT NULL REFERENCES acc_journal_entries(id) ON DELETE CASCADE,
    account_id       BIGINT        REFERENCES acc_accounts(id),
    debit            NUMERIC(18,2) DEFAULT 0,
    credit           NUMERIC(18,2) DEFAULT 0,
    description      VARCHAR(255)
);

-- -------------------------------------------------------
-- acc_sync_log  – tracks which sales/POs have been journalised
-- -------------------------------------------------------
CREATE TABLE acc_sync_log (
    id               BIGSERIAL PRIMARY KEY,
    entity_type      VARCHAR(30)  NOT NULL,   -- SALE / PURCHASE_ORDER
    entity_id        BIGINT       NOT NULL,
    journal_entry_id BIGINT       REFERENCES acc_journal_entries(id),
    synced_at        TIMESTAMP    DEFAULT NOW(),
    UNIQUE (entity_type, entity_id)
);

-- -------------------------------------------------------
-- Seed: chart of accounts
-- -------------------------------------------------------
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
-- Level 1 (root groups)
('1',       'Activos',                           'ASSET',     'DEBIT',  NULL, FALSE, TRUE),
('2',       'Pasivos',                           'LIABILITY', 'CREDIT', NULL, FALSE, TRUE),
('3',       'Patrimonio',                        'EQUITY',    'CREDIT', NULL, FALSE, TRUE),
('4',       'Ingresos',                          'REVENUE',   'CREDIT', NULL, FALSE, TRUE),
('5',       'Gastos',                            'EXPENSE',   'DEBIT',  NULL, FALSE, TRUE);

-- Level 2 – Activos
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('1.1',   'Activo Corriente',     'ASSET', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '1'), FALSE, TRUE),
('1.2',   'Activo No Corriente',  'ASSET', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '1'), FALSE, TRUE);

-- Level 2 – Pasivos
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('2.1',   'Pasivo Corriente',     'LIABILITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '2'), FALSE, TRUE),
('2.2',   'Pasivo No Corriente',  'LIABILITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '2'), FALSE, TRUE);

-- Level 2 – Patrimonio (leaf nodes)
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('3.1',   'Capital Social',          'EQUITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '3'), TRUE, TRUE),
('3.2',   'Utilidades Retenidas',    'EQUITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '3'), TRUE, TRUE),
('3.3',   'Resultado del Ejercicio', 'EQUITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '3'), TRUE, TRUE);

-- Level 2 – Ingresos
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('4.1',   'Ingresos Operacionales', 'REVENUE', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '4'), FALSE, TRUE),
('4.2',   'Otros Ingresos',         'REVENUE', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '4'), TRUE,  TRUE);

-- Level 2 – Gastos
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('5.1',   'Costo de Ventas',     'EXPENSE', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '5'), TRUE,  TRUE),
('5.2',   'Gastos Operativos',   'EXPENSE', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '5'), FALSE, TRUE),
('5.3',   'Gastos Financieros',  'EXPENSE', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '5'), TRUE,  TRUE);

-- Level 3 – Activo Corriente sub-groups
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('1.1.1', 'Efectivo y Equivalentes', 'ASSET', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '1.1'), FALSE, TRUE),
('1.1.2', 'Cuentas por Cobrar',      'ASSET', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '1.1'), TRUE,  TRUE),
('1.1.3', 'Inventario de Mercancías','ASSET', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '1.1'), TRUE,  TRUE),
('1.1.4', 'ITBIS Pagado por Anticipado','ASSET','DEBIT',(SELECT id FROM acc_accounts WHERE code = '1.1'), TRUE,  TRUE);

-- Level 3 – Activo No Corriente
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('1.2.1', 'Propiedad, Planta y Equipo', 'ASSET', 'DEBIT',  (SELECT id FROM acc_accounts WHERE code = '1.2'), TRUE, TRUE),
('1.2.2', 'Depreciación Acumulada',     'ASSET', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '1.2'), TRUE, TRUE);

-- Level 3 – Pasivo Corriente
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('2.1.1', 'Cuentas por Pagar',    'LIABILITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '2.1'), TRUE, TRUE),
('2.1.2', 'ITBIS por Pagar',      'LIABILITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '2.1'), TRUE, TRUE),
('2.1.3', 'Nóminas por Pagar',    'LIABILITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '2.1'), TRUE, TRUE);

-- Level 3 – Pasivo No Corriente
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('2.2.1', 'Préstamos Bancarios',  'LIABILITY', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '2.2'), TRUE, TRUE);

-- Level 3 – Ingresos Operacionales
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('4.1.1', 'Ventas de Bienes',     'REVENUE', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '4.1'), TRUE, TRUE),
('4.1.2', 'Ventas de Servicios',  'REVENUE', 'CREDIT', (SELECT id FROM acc_accounts WHERE code = '4.1'), TRUE, TRUE);

-- Level 3 – Gastos Operativos
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('5.2.1', 'Gastos de Administración',     'EXPENSE', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '5.2'), TRUE, TRUE),
('5.2.2', 'Gastos de Ventas',             'EXPENSE', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '5.2'), TRUE, TRUE),
('5.2.3', 'Gastos de Operación General',  'EXPENSE', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '5.2'), TRUE, TRUE);

-- Level 4 – Efectivo y Equivalentes
INSERT INTO acc_accounts (code, name, type, nature, parent_id, allows_transactions, is_system)
VALUES
('1.1.1.01', 'Caja',  'ASSET', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '1.1.1'), TRUE, TRUE),
('1.1.1.02', 'Banco', 'ASSET', 'DEBIT', (SELECT id FROM acc_accounts WHERE code = '1.1.1'), TRUE, TRUE);
