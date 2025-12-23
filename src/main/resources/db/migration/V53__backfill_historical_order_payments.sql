-- Backfill Payments and Journal Entries for Historical Orders
-- This migration creates financial records for orders that were completed before the financial system was set up

-- Step 1: Create payments for completed orders that don't have payment records
INSERT INTO payments (
    order_id,
    method,
    status,
    amount,
    tip_amount,
    transaction_id,
    paid_at,
    completed_at,
    created_at,
    updated_at
)
SELECT
    o.id as order_id,
    'CASH' as method,  -- Default to CASH for historical orders
    'COMPLETED' as status,
    o.total as amount,
    COALESCE(o.tip_amount, 0) as tip_amount,
    CONCAT('HIST-', o.id, '-', TO_CHAR(o.created_at, 'YYYYMMDD')) as transaction_id,
    COALESCE(o.completed_at, o.updated_at, o.created_at) as paid_at,
    COALESCE(o.completed_at, o.updated_at, o.created_at) as completed_at,
    NOW() as created_at,
    NOW() as updated_at
FROM orders o
WHERE o.status IN ('READY', 'PICKED_UP', 'DELIVERED', 'COMPLETED')
  AND o.total > 0
  AND NOT EXISTS (
      SELECT 1 FROM payments p WHERE p.order_id = o.id
  );

-- Step 2: Update order payment_status for orders that now have payments
UPDATE orders o
SET payment_status = 'COMPLETED'
WHERE o.status IN ('READY', 'PICKED_UP', 'DELIVERED', 'COMPLETED')
  AND o.payment_status IS NULL
  AND EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id AND p.status = 'COMPLETED');

-- Step 3: Create journal entries for completed orders (revenue recording)
-- Only for restaurants that have accounts initialized
INSERT INTO financial_journal_entries (
    restaurant_id,
    entry_number,
    entry_date,
    description,
    reference_type,
    reference_id,
    total_debit,
    total_credit,
    balanced,
    status,
    created_by,
    created_at,
    updated_at
)
SELECT
    o.restaurant_id,
    CONCAT('JE-HIST-', o.id) as entry_number,
    DATE(COALESCE(o.completed_at, o.created_at)) as entry_date,
    CONCAT('Historical Sales from Order #', o.order_number) as description,
    'ORDER' as reference_type,
    o.id as reference_id,
    o.total as total_debit,
    o.total as total_credit,
    true as balanced,
    'POSTED' as status,
    'SYSTEM_MIGRATION' as created_by,
    NOW() as created_at,
    NOW() as updated_at
FROM orders o
WHERE o.status IN ('READY', 'PICKED_UP', 'DELIVERED', 'COMPLETED')
  AND o.total > 0
  AND EXISTS (
      SELECT 1 FROM financial_accounts fa
      WHERE fa.restaurant_id = o.restaurant_id
      AND fa.category = 'CASH'
  )
  AND NOT EXISTS (
      SELECT 1 FROM financial_journal_entries je
      WHERE je.reference_type = 'ORDER'
      AND je.reference_id = o.id
  );

-- Step 4: Create debit transactions (Cash account increases)
INSERT INTO financial_transactions (
    restaurant_id,
    account_id,
    journal_entry_id,
    transaction_date,
    type,
    amount,
    balance_before,
    balance_after,
    reference_type,
    reference_id,
    description,
    performed_by,
    created_at
)
SELECT
    je.restaurant_id,
    cash_acc.id as account_id,
    je.id as journal_entry_id,
    je.entry_date as transaction_date,
    'DEBIT' as type,
    je.total_debit as amount,
    cash_acc.balance as balance_before,
    cash_acc.balance + je.total_debit as balance_after,
    je.reference_type,
    je.reference_id,
    je.description,
    'SYSTEM_MIGRATION' as performed_by,
    NOW() as created_at
FROM financial_journal_entries je
JOIN financial_accounts cash_acc ON cash_acc.restaurant_id = je.restaurant_id AND cash_acc.category = 'CASH'
WHERE je.reference_type = 'ORDER'
  AND je.created_by = 'SYSTEM_MIGRATION'
  AND NOT EXISTS (
      SELECT 1 FROM financial_transactions ft
      WHERE ft.journal_entry_id = je.id
      AND ft.type = 'DEBIT'
  );

-- Step 5: Create credit transactions (Sales Revenue account increases)
INSERT INTO financial_transactions (
    restaurant_id,
    account_id,
    journal_entry_id,
    transaction_date,
    type,
    amount,
    balance_before,
    balance_after,
    reference_type,
    reference_id,
    description,
    performed_by,
    created_at
)
SELECT
    je.restaurant_id,
    sales_acc.id as account_id,
    je.id as journal_entry_id,
    je.entry_date as transaction_date,
    'CREDIT' as type,
    je.total_credit as amount,
    sales_acc.balance as balance_before,
    sales_acc.balance + je.total_credit as balance_after,
    je.reference_type,
    je.reference_id,
    je.description,
    'SYSTEM_MIGRATION' as performed_by,
    NOW() as created_at
FROM financial_journal_entries je
JOIN financial_accounts sales_acc ON sales_acc.restaurant_id = je.restaurant_id AND sales_acc.category = 'SALES'
WHERE je.reference_type = 'ORDER'
  AND je.created_by = 'SYSTEM_MIGRATION'
  AND NOT EXISTS (
      SELECT 1 FROM financial_transactions ft
      WHERE ft.journal_entry_id = je.id
      AND ft.type = 'CREDIT'
  );

-- Step 6: Update account balances based on transactions
-- Update Cash account balances
UPDATE financial_accounts fa
SET balance = (
    SELECT COALESCE(SUM(
        CASE
            WHEN ft.type = 'DEBIT' AND fa.normal_balance = 'DEBIT' THEN ft.amount
            WHEN ft.type = 'CREDIT' AND fa.normal_balance = 'CREDIT' THEN ft.amount
            WHEN ft.type = 'DEBIT' AND fa.normal_balance = 'CREDIT' THEN -ft.amount
            WHEN ft.type = 'CREDIT' AND fa.normal_balance = 'DEBIT' THEN -ft.amount
            ELSE 0
        END
    ), 0)
    FROM financial_transactions ft
    WHERE ft.account_id = fa.id
),
updated_at = NOW()
WHERE fa.category IN ('CASH', 'SALES')
  AND EXISTS (
      SELECT 1 FROM financial_transactions ft
      WHERE ft.account_id = fa.id
      AND ft.performed_by = 'SYSTEM_MIGRATION'
  );
