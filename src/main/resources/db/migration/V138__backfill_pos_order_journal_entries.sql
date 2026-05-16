-- Backfill journal entries for historical COMPLETED / DELIVERED orders that
-- never produced a financial_journal_entries row. POSOrderService.createOrder
-- used to set status=COMPLETED inline without calling RevenueService, so the
-- whole POS sales history was missing from the double-entry ledger — the
-- balance sheet showed bank/cash drained by PO outflows with no offsetting
-- revenue. The code fix prevents new orders from leaking; this repairs the
-- past.
--
-- Scope: main sales line only (Dr Cash/Bank, Cr Sales). COGS / service fee
-- / delivery fee / discount / refund journals are NOT backfilled — they'd
-- require per-item costs at sale time which have likely drifted. Inventory
-- balance is therefore left as-is.
--
-- Safety:
--   * Idempotent. Re-running won't double-post because each step guards on
--     "no existing journal entry / transaction with reference_id = order.id".
--   * Skips orders whose restaurant doesn't have SALES + (CASH or BANK)
--     accounts — running this before chart-of-accounts init is a no-op.

DO $$
DECLARE
  v_jes INTEGER := 0;
BEGIN
  ------------------------------------------------------------------
  -- Step 1: one journal entry per missing order.
  ------------------------------------------------------------------
  INSERT INTO financial_journal_entries (
      restaurant_id, entry_number, entry_date, description,
      reference_type, reference_id, total_debit, total_credit,
      balanced, status, created_by, created_at, updated_at)
  SELECT
      o.restaurant_id,
      'JE-BFP-' || o.id,
      COALESCE(o.completed_at::date, o.created_at::date),
      'Backfilled revenue for Order #' || o.id,
      'ORDER',
      o.id,
      o.total,
      o.total,
      TRUE,
      'POSTED',
      'BACKFILL_V138',
      NOW(),
      NOW()
  FROM orders o
  WHERE o.status IN ('COMPLETED', 'DELIVERED')
    AND o.total IS NOT NULL
    AND o.total > 0
    AND NOT EXISTS (
        SELECT 1 FROM financial_journal_entries je
        WHERE je.reference_type = 'ORDER' AND je.reference_id = o.id
    )
    AND EXISTS (
        SELECT 1 FROM financial_accounts a
        WHERE a.restaurant_id = o.restaurant_id AND a.category = 'SALES'
    )
    AND EXISTS (
        SELECT 1 FROM financial_accounts a
        WHERE a.restaurant_id = o.restaurant_id AND a.category IN ('CASH', 'BANK')
    );

  GET DIAGNOSTICS v_jes = ROW_COUNT;
  RAISE NOTICE 'V138 backfill: created % journal entries', v_jes;

  ------------------------------------------------------------------
  -- Step 2: DEBIT transactions. Route to CASH for cash payments
  -- (or when no payment row exists), BANK for every other method
  -- (card / mobile / online / wallet / bank transfer / etc.).
  ------------------------------------------------------------------
  INSERT INTO financial_transactions (
      restaurant_id, account_id, journal_entry_id, transaction_date,
      type, amount, balance_before, balance_after,
      reference_type, reference_id, description, performed_by, created_at)
  SELECT
      je.restaurant_id,
      a.id,
      je.id,
      je.entry_date,
      'DEBIT',
      je.total_debit,
      NULL,                                     -- balance_before unknown on backfill
      NULL,                                     -- balance_after unknown on backfill
      'ORDER',
      je.reference_id,
      je.description,
      'BACKFILL_V138',
      NOW()
  FROM financial_journal_entries je
  LEFT JOIN payments p ON p.order_id = je.reference_id
  JOIN financial_accounts a
        ON a.restaurant_id = je.restaurant_id
       AND a.category = CASE
             WHEN p.method IS NULL OR p.method = 'CASH' THEN 'CASH'
             ELSE 'BANK'
           END
  WHERE je.created_by = 'BACKFILL_V138'
    AND NOT EXISTS (
        SELECT 1 FROM financial_transactions t
        WHERE t.journal_entry_id = je.id AND t.type = 'DEBIT'
    );

  ------------------------------------------------------------------
  -- Step 3: CREDIT transactions (Sales).
  ------------------------------------------------------------------
  INSERT INTO financial_transactions (
      restaurant_id, account_id, journal_entry_id, transaction_date,
      type, amount, balance_before, balance_after,
      reference_type, reference_id, description, performed_by, created_at)
  SELECT
      je.restaurant_id,
      a.id,
      je.id,
      je.entry_date,
      'CREDIT',
      je.total_credit,
      NULL,
      NULL,
      'ORDER',
      je.reference_id,
      je.description,
      'BACKFILL_V138',
      NOW()
  FROM financial_journal_entries je
  JOIN financial_accounts a
        ON a.restaurant_id = je.restaurant_id
       AND a.category = 'SALES'
  WHERE je.created_by = 'BACKFILL_V138'
    AND NOT EXISTS (
        SELECT 1 FROM financial_transactions t
        WHERE t.journal_entry_id = je.id AND t.type = 'CREDIT'
    );

  ------------------------------------------------------------------
  -- Step 4: update financial_accounts.balance to reflect the new
  -- transactions. Account.updateBalance() in code adds the amount to
  -- the balance in the account's natural sign (asset/expense DEBIT
  -- increases, liability/revenue/equity CREDIT increases), so a
  -- straight "balance += SUM(amount)" over our backfill transactions
  -- is the correct online-equivalent update.
  ------------------------------------------------------------------
  UPDATE financial_accounts a
  SET balance = a.balance + agg.delta,
      updated_at = NOW()
  FROM (
      SELECT t.account_id, SUM(t.amount) AS delta
      FROM financial_transactions t
      WHERE t.performed_by = 'BACKFILL_V138'
      GROUP BY t.account_id
  ) agg
  WHERE a.id = agg.account_id;
END $$;
