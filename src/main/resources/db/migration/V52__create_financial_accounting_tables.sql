-- Financial Accounting Tables for Revenue, Expenses, and Reporting

-- Financial Accounts (Chart of Accounts)
CREATE TABLE IF NOT EXISTS financial_accounts (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL, -- ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    category VARCHAR(50) NOT NULL, -- CASH, BANK, INVENTORY, COGS, SALES, LABOR, etc.
    description VARCHAR(500),
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    normal_balance VARCHAR(20) NOT NULL, -- DEBIT or CREDIT
    parent_account_id BIGINT REFERENCES financial_accounts(id),
    active BOOLEAN NOT NULL DEFAULT true,
    system_account BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (restaurant_id, code)
);

CREATE INDEX idx_financial_accounts_restaurant ON financial_accounts(restaurant_id);
CREATE INDEX idx_financial_accounts_type ON financial_accounts(type);
CREATE INDEX idx_financial_accounts_category ON financial_accounts(category);

-- Financial Journal Entries (Double-Entry Bookkeeping)
CREATE TABLE IF NOT EXISTS financial_journal_entries (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    entry_number VARCHAR(50) NOT NULL,
    entry_date DATE NOT NULL,
    description VARCHAR(500),
    reference_type VARCHAR(100), -- ORDER, PURCHASE_ORDER, EXPENSE, PAYROLL, etc.
    reference_id BIGINT,
    total_debit DECIMAL(15, 2) NOT NULL,
    total_credit DECIMAL(15, 2) NOT NULL,
    balanced BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, POSTED, REVERSED, VOID
    created_by VARCHAR(100),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_journal_entry_restaurant ON financial_journal_entries(restaurant_id);
CREATE INDEX idx_journal_entry_date ON financial_journal_entries(entry_date);
CREATE INDEX idx_journal_entry_reference ON financial_journal_entries(reference_type, reference_id);
CREATE INDEX idx_journal_entry_status ON financial_journal_entries(status);

-- Financial Transactions (Individual debit/credit lines)
CREATE TABLE IF NOT EXISTS financial_transactions (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES financial_accounts(id) ON DELETE CASCADE,
    journal_entry_id BIGINT REFERENCES financial_journal_entries(id) ON DELETE SET NULL,
    transaction_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL, -- DEBIT or CREDIT
    amount DECIMAL(15, 2) NOT NULL,
    balance_before DECIMAL(15, 2),
    balance_after DECIMAL(15, 2),
    reference_type VARCHAR(100), -- ORDER, PURCHASE_ORDER, EXPENSE, PAYROLL, etc.
    reference_id BIGINT,
    description VARCHAR(500),
    notes VARCHAR(1000),
    performed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_financial_transaction_restaurant ON financial_transactions(restaurant_id);
CREATE INDEX idx_financial_transaction_date ON financial_transactions(transaction_date);
CREATE INDEX idx_financial_transaction_account ON financial_transactions(account_id);
CREATE INDEX idx_financial_transaction_reference ON financial_transactions(reference_type, reference_id);
CREATE INDEX idx_financial_transaction_journal ON financial_transactions(journal_entry_id);
