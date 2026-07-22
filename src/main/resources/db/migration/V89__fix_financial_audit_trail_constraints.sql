-- Migration: Fix financial audit trail constraints
-- Purpose: Financial transactions must never be cascaded when accounts/journal entries are deleted
-- This maintains audit trail integrity for financial compliance (SOX, GAAP)

-- 1. Fix CASCADE DELETE on financial_transactions.account_id
-- Transactions should be preserved when accounts are soft-deleted
-- Change from ON DELETE CASCADE to ON DELETE RESTRICT
ALTER TABLE financial_transactions
    DROP CONSTRAINT IF EXISTS financial_transactions_account_id_fkey;

ALTER TABLE financial_transactions
    ADD CONSTRAINT financial_transactions_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES financial_accounts(id) ON DELETE RESTRICT;

-- 2. Fix CASCADE DELETE on financial_transactions.journal_entry_id
-- Transactions should be preserved when journal entries are modified
-- Change from ON DELETE CASCADE/SET NULL to ON DELETE RESTRICT
ALTER TABLE financial_transactions
    DROP CONSTRAINT IF EXISTS financial_transactions_journal_entry_id_fkey;

ALTER TABLE financial_transactions
    ADD CONSTRAINT financial_transactions_journal_entry_id_fkey
    FOREIGN KEY (journal_entry_id) REFERENCES financial_journal_entries(id) ON DELETE RESTRICT;

-- 3. Add CHECK constraint for debit/credit equality on journal entries
-- When a journal entry is marked as balanced, total_debit must equal total_credit
-- This enforces double-entry bookkeeping integrity at the database level
ALTER TABLE financial_journal_entries
    ADD CONSTRAINT chk_journal_entry_balanced
    CHECK (balanced = false OR total_debit = total_credit);

-- 4. Add amount positive constraint on financial_transactions
-- Transaction amounts must always be positive (type determines debit/credit direction)
ALTER TABLE financial_transactions
    ADD CONSTRAINT chk_transaction_amount_positive
    CHECK (amount > 0);

-- Add comments explaining the constraints
COMMENT ON CONSTRAINT financial_transactions_account_id_fkey ON financial_transactions IS
    'RESTRICT delete to preserve audit trail - accounts must be soft-deleted instead';

COMMENT ON CONSTRAINT financial_transactions_journal_entry_id_fkey ON financial_transactions IS
    'RESTRICT delete to preserve audit trail - journal entries must be reversed, not deleted';

COMMENT ON CONSTRAINT chk_journal_entry_balanced ON financial_journal_entries IS
    'Enforces double-entry bookkeeping: when balanced=true, debits must equal credits';

COMMENT ON CONSTRAINT chk_transaction_amount_positive ON financial_transactions IS
    'Transaction amounts must be positive - type field determines debit/credit direction';
