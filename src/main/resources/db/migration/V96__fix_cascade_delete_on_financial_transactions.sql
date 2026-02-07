-- Migration: Fix CASCADE DELETE on financial_transactions to preserve audit trail
-- Problem: Deleting a financial_account cascades to delete all related transactions,
--          destroying the audit trail. Financial records should be immutable for compliance.

-- Step 1: Drop the existing foreign key constraint on account_id
ALTER TABLE financial_transactions
    DROP CONSTRAINT IF EXISTS financial_transactions_account_id_fkey;

-- Step 2: Re-add the constraint with RESTRICT instead of CASCADE
-- This prevents deletion of accounts that have transaction history
ALTER TABLE financial_transactions
    ADD CONSTRAINT financial_transactions_account_id_fkey
    FOREIGN KEY (account_id)
    REFERENCES financial_accounts(id)
    ON DELETE RESTRICT;

-- Step 3: Add a CHECK constraint to ensure balanced journal entries have equal debits and credits
-- Note: This applies only when the entry is marked as balanced
ALTER TABLE financial_journal_entries
    ADD CONSTRAINT chk_journal_entry_balanced
    CHECK (balanced = false OR total_debit = total_credit);

-- Step 4: Add comment explaining the constraint
COMMENT ON CONSTRAINT financial_transactions_account_id_fkey ON financial_transactions
    IS 'RESTRICT prevents account deletion when transactions exist, preserving audit trail';

COMMENT ON CONSTRAINT chk_journal_entry_balanced ON financial_journal_entries
    IS 'Ensures debit and credit totals are equal when journal entry is marked as balanced';
