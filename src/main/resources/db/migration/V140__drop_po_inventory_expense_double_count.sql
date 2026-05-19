-- Remove the historical INVENTORY-as-expense double-count.
--
-- PurchaseOrderService used to create a financial_expenses row with
-- category = INVENTORY every time a PO was fully received. That was a
-- double-count: the receipt journal already debits Inventory (asset) /
-- credits A/P, and the period expense for sold inventory is recognised
-- later via COGS. The Expense row added the full PO value as a period
-- expense AGAIN, dragging net profit down on the dashboard, the daily
-- Telegram report, and the P&L.
--
-- The code path that created these rows is removed. This migration
-- soft-deletes the historical evidence so reports stop reading them.
-- Identified by either (purchase_order_id IS NOT NULL) or
-- (category = 'INVENTORY' with a description matching the PO pattern
-- — backstop for older rows that may have been created before the
-- purchase_order_id column landed).
UPDATE financial_expenses
SET deleted_at = NOW(),
    deleted_by = 'V140_BACKFILL',
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND (
      purchase_order_id IS NOT NULL
      OR (category = 'INVENTORY' AND description LIKE 'Purchase Order:%')
  );
