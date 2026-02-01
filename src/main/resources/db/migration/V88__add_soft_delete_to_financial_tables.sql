-- Migration: Add soft delete columns to financial tables
-- Purpose: Financial records should never be hard deleted for audit compliance (SOX, GAAP)
-- This migration adds deleted_at and deleted_by columns to track soft deletions

-- Add soft delete columns to financial_expenses table
ALTER TABLE financial_expenses
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- Create index for efficient filtering of non-deleted expenses
CREATE INDEX IF NOT EXISTS idx_expense_deleted_at ON financial_expenses(deleted_at);

-- Add soft delete columns to financial_accounts table
ALTER TABLE financial_accounts
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- Create index for efficient filtering of non-deleted accounts
CREATE INDEX IF NOT EXISTS idx_account_deleted_at ON financial_accounts(deleted_at);

-- Add soft delete columns to financial_payroll_entries table
ALTER TABLE financial_payroll_entries
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- Create index for efficient filtering of non-deleted payroll entries
CREATE INDEX IF NOT EXISTS idx_payroll_deleted_at ON financial_payroll_entries(deleted_at);

-- Note: The payments table already has deleted_at and deleted_by columns
-- (added in a previous migration), so no changes needed there.

-- Add comments explaining the soft delete policy
COMMENT ON COLUMN financial_expenses.deleted_at IS 'Timestamp when record was soft deleted. NULL means active record.';
COMMENT ON COLUMN financial_expenses.deleted_by IS 'Username of person who soft deleted the record.';

COMMENT ON COLUMN financial_accounts.deleted_at IS 'Timestamp when record was soft deleted. NULL means active record.';
COMMENT ON COLUMN financial_accounts.deleted_by IS 'Username of person who soft deleted the record.';

COMMENT ON COLUMN financial_payroll_entries.deleted_at IS 'Timestamp when record was soft deleted. NULL means active record.';
COMMENT ON COLUMN financial_payroll_entries.deleted_by IS 'Username of person who soft deleted the record.';
