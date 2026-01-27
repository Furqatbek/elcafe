-- Add commission percentage field to waiters table
ALTER TABLE waiters ADD COLUMN IF NOT EXISTS commission_percent DECIMAL(5, 2) DEFAULT 0.00;
ALTER TABLE waiters ADD COLUMN IF NOT EXISTS commission_enabled BOOLEAN DEFAULT false;

-- Create waiter commission tracking table
CREATE TABLE IF NOT EXISTS waiter_commissions (
    id BIGSERIAL PRIMARY KEY,
    waiter_id BIGINT NOT NULL REFERENCES waiters(id) ON DELETE CASCADE,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,

    -- Commission calculation details
    order_total DECIMAL(12, 2) NOT NULL,
    commission_percent DECIMAL(5, 2) NOT NULL,
    commission_amount DECIMAL(12, 2) NOT NULL,

    -- Status tracking
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Payroll integration
    payroll_entry_id BIGINT REFERENCES financial_payroll_entries(id) ON DELETE SET NULL,
    paid_at TIMESTAMP,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Prevent duplicate commissions for same order
    CONSTRAINT unique_waiter_order_commission UNIQUE (waiter_id, order_id)
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_waiter_commissions_waiter_id ON waiter_commissions(waiter_id);
CREATE INDEX IF NOT EXISTS idx_waiter_commissions_order_id ON waiter_commissions(order_id);
CREATE INDEX IF NOT EXISTS idx_waiter_commissions_restaurant_id ON waiter_commissions(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_waiter_commissions_status ON waiter_commissions(status);
CREATE INDEX IF NOT EXISTS idx_waiter_commissions_created_at ON waiter_commissions(created_at);
CREATE INDEX IF NOT EXISTS idx_waiter_commissions_payroll_entry_id ON waiter_commissions(payroll_entry_id);

-- Create commission summary view for reporting
CREATE OR REPLACE VIEW waiter_commission_summary AS
SELECT
    w.id AS waiter_id,
    w.name AS waiter_name,
    w.commission_percent AS current_commission_percent,
    COUNT(wc.id) AS total_commissions,
    COALESCE(SUM(wc.order_total), 0) AS total_order_value,
    COALESCE(SUM(wc.commission_amount), 0) AS total_commission_earned,
    COALESCE(SUM(CASE WHEN wc.status = 'PENDING' THEN wc.commission_amount ELSE 0 END), 0) AS pending_commission,
    COALESCE(SUM(CASE WHEN wc.status = 'PAID' THEN wc.commission_amount ELSE 0 END), 0) AS paid_commission,
    MIN(wc.created_at) AS first_commission_date,
    MAX(wc.created_at) AS last_commission_date
FROM waiters w
LEFT JOIN waiter_commissions wc ON w.id = wc.waiter_id
GROUP BY w.id, w.name, w.commission_percent;

-- Add waiter_id column to financial_payroll_entries if not exists
ALTER TABLE financial_payroll_entries ADD COLUMN IF NOT EXISTS waiter_id BIGINT REFERENCES waiters(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_payroll_entries_waiter_id ON financial_payroll_entries(waiter_id);

COMMENT ON TABLE waiter_commissions IS 'Tracks commission earned by waiters from each order';
COMMENT ON COLUMN waiters.commission_percent IS 'Default commission percentage for this waiter (0-100)';
COMMENT ON COLUMN waiters.commission_enabled IS 'Whether commission tracking is enabled for this waiter';
