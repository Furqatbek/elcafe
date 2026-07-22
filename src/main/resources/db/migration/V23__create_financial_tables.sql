-- Create financial_accounts table (Chart of Accounts)
CREATE TABLE financial_accounts (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    normal_balance VARCHAR(20) NOT NULL,
    parent_account_id BIGINT REFERENCES financial_accounts(id) ON DELETE SET NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    system_account BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_account_code_per_restaurant UNIQUE (restaurant_id, code)
);

CREATE INDEX idx_financial_accounts_restaurant ON financial_accounts(restaurant_id);
CREATE INDEX idx_financial_accounts_type ON financial_accounts(type);
CREATE INDEX idx_financial_accounts_category ON financial_accounts(category);
CREATE INDEX idx_financial_accounts_parent ON financial_accounts(parent_account_id);

-- Create financial_accounting_periods table
CREATE TABLE financial_accounting_periods (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_at TIMESTAMP,
    closed_by VARCHAR(100),
    closing_notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_accounting_period_restaurant ON financial_accounting_periods(restaurant_id);
CREATE INDEX idx_accounting_period_dates ON financial_accounting_periods(start_date, end_date);

-- Create financial_journal_entries table
CREATE TABLE financial_journal_entries (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    entry_number VARCHAR(50) NOT NULL,
    entry_date DATE NOT NULL,
    description VARCHAR(500),
    reference_type VARCHAR(100),
    reference_id BIGINT,
    total_debit DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_credit DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    balanced BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_journal_entry_restaurant ON financial_journal_entries(restaurant_id);
CREATE INDEX idx_journal_entry_date ON financial_journal_entries(entry_date);
CREATE INDEX idx_journal_entry_reference ON financial_journal_entries(reference_type, reference_id);

-- Create financial_transactions table
CREATE TABLE financial_transactions (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES financial_accounts(id) ON DELETE CASCADE,
    journal_entry_id BIGINT REFERENCES financial_journal_entries(id) ON DELETE CASCADE,
    transaction_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    balance_before DECIMAL(15, 2),
    balance_after DECIMAL(15, 2),
    reference_type VARCHAR(100),
    reference_id BIGINT,
    description VARCHAR(500),
    notes VARCHAR(1000),
    performed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_financial_transaction_restaurant ON financial_transactions(restaurant_id);
CREATE INDEX idx_financial_transaction_date ON financial_transactions(transaction_date);
CREATE INDEX idx_financial_transaction_account ON financial_transactions(account_id);
CREATE INDEX idx_financial_transaction_journal ON financial_transactions(journal_entry_id);
CREATE INDEX idx_financial_transaction_reference ON financial_transactions(reference_type, reference_id);

-- Create financial_purchase_orders table
CREATE TABLE financial_purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    po_number VARCHAR(50) NOT NULL UNIQUE,
    supplier_name VARCHAR(200) NOT NULL,
    supplier_contact VARCHAR(200),
    supplier_address VARCHAR(500),
    order_date DATE NOT NULL,
    expected_delivery_date DATE,
    actual_delivery_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    subtotal DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(15, 2) DEFAULT 0.00,
    shipping_cost DECIMAL(15, 2) DEFAULT 0.00,
    total_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    paid_amount DECIMAL(15, 2) DEFAULT 0.00,
    payment_status VARCHAR(20) DEFAULT 'UNPAID',
    notes VARCHAR(1000),
    created_by VARCHAR(100),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    received_by VARCHAR(100),
    received_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purchase_order_restaurant ON financial_purchase_orders(restaurant_id);
CREATE INDEX idx_purchase_order_number ON financial_purchase_orders(po_number);
CREATE INDEX idx_purchase_order_supplier ON financial_purchase_orders(supplier_name);
CREATE INDEX idx_purchase_order_date ON financial_purchase_orders(order_date);

-- Create financial_purchase_order_items table
CREATE TABLE financial_purchase_order_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES financial_purchase_orders(id) ON DELETE CASCADE,
    ingredient_id BIGINT REFERENCES inventory_ingredients(id) ON DELETE SET NULL,
    item_name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    sku VARCHAR(50),
    quantity DECIMAL(10, 3) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_price DECIMAL(15, 2) NOT NULL,
    total_price DECIMAL(15, 2) NOT NULL,
    received_quantity DECIMAL(10, 3) DEFAULT 0.000,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_po_item_purchase_order ON financial_purchase_order_items(purchase_order_id);
CREATE INDEX idx_po_item_ingredient ON financial_purchase_order_items(ingredient_id);

-- Create financial_expenses table
CREATE TABLE financial_expenses (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    account_id BIGINT REFERENCES financial_accounts(id) ON DELETE SET NULL,
    expense_number VARCHAR(50) NOT NULL,
    expense_date DATE NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(200) NOT NULL,
    vendor VARCHAR(200),
    amount DECIMAL(15, 2) NOT NULL,
    tax_amount DECIMAL(15, 2) DEFAULT 0.00,
    total_amount DECIMAL(15, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'CASH',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    payment_date DATE,
    reference_number VARCHAR(100),
    notes VARCHAR(1000),
    recurring BOOLEAN NOT NULL DEFAULT FALSE,
    recurring_period VARCHAR(20),
    attachment_url VARCHAR(500),
    created_by VARCHAR(100),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_expense_restaurant ON financial_expenses(restaurant_id);
CREATE INDEX idx_expense_date ON financial_expenses(expense_date);
CREATE INDEX idx_expense_category ON financial_expenses(category);
CREATE INDEX idx_expense_account ON financial_expenses(account_id);

-- Create financial_payroll_entries table
CREATE TABLE financial_payroll_entries (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payroll_number VARCHAR(50) NOT NULL,
    pay_period_start DATE NOT NULL,
    pay_period_end DATE NOT NULL,
    payment_date DATE,
    payroll_type VARCHAR(20) NOT NULL,
    hours_worked DECIMAL(10, 2),
    hourly_rate DECIMAL(10, 2),
    base_salary DECIMAL(15, 2) DEFAULT 0.00,
    overtime_pay DECIMAL(15, 2) DEFAULT 0.00,
    bonus DECIMAL(15, 2) DEFAULT 0.00,
    tips DECIMAL(15, 2) DEFAULT 0.00,
    commission DECIMAL(15, 2) DEFAULT 0.00,
    gross_pay DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    tax_deduction DECIMAL(15, 2) DEFAULT 0.00,
    social_security_deduction DECIMAL(15, 2) DEFAULT 0.00,
    health_insurance_deduction DECIMAL(15, 2) DEFAULT 0.00,
    other_deductions DECIMAL(15, 2) DEFAULT 0.00,
    total_deductions DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    net_pay DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(20),
    notes VARCHAR(1000),
    processed_by VARCHAR(100),
    processed_at TIMESTAMP,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payroll_restaurant ON financial_payroll_entries(restaurant_id);
CREATE INDEX idx_payroll_employee ON financial_payroll_entries(employee_id);
CREATE INDEX idx_payroll_period ON financial_payroll_entries(pay_period_start, pay_period_end);
CREATE INDEX idx_payroll_date ON financial_payroll_entries(payment_date);

-- Add comments
COMMENT ON TABLE financial_accounts IS 'Chart of accounts for double-entry bookkeeping';
COMMENT ON TABLE financial_accounting_periods IS 'Fiscal periods for organizing financial data';
COMMENT ON TABLE financial_journal_entries IS 'Journal entries for double-entry accounting';
COMMENT ON TABLE financial_transactions IS 'Individual debit/credit transactions';
COMMENT ON TABLE financial_purchase_orders IS 'Purchase orders for supplier inventory purchases';
COMMENT ON TABLE financial_purchase_order_items IS 'Line items for purchase orders';
COMMENT ON TABLE financial_expenses IS 'Operational expenses tracking';
COMMENT ON TABLE financial_payroll_entries IS 'Employee payroll and compensation';

COMMENT ON COLUMN financial_accounts.type IS 'ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE';
COMMENT ON COLUMN financial_accounts.normal_balance IS 'DEBIT or CREDIT - determines how balance increases';
COMMENT ON COLUMN financial_accounts.system_account IS 'System accounts cannot be deleted';

COMMENT ON COLUMN financial_transactions.type IS 'DEBIT or CREDIT';
COMMENT ON COLUMN financial_transactions.reference_type IS 'ORDER, PURCHASE_ORDER, EXPENSE, PAYROLL, etc.';

COMMENT ON COLUMN financial_expenses.recurring IS 'Whether this is a recurring expense';
COMMENT ON COLUMN financial_expenses.recurring_period IS 'WEEKLY, MONTHLY, QUARTERLY, YEARLY';

COMMENT ON COLUMN financial_payroll_entries.payroll_type IS 'HOURLY, SALARY, CONTRACT, COMMISSION';
