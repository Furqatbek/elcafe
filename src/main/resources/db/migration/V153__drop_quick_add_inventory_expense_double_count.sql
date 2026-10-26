-- Finish the INVENTORY-as-expense cleanup that V140 started.
--
-- V140 removed the expense row that a fully-received purchase order used
-- to create, and soft-deleted the historical rows. It missed one path:
-- PurchaseOrderService.createAndFinalize (the "Quick Add" cash market
-- purchase) kept booking the same row, on the argument that such a
-- purchase has no later COGS recognition to collide with. It does.
-- createAndFinalize receives the PO through the ordinary path, so the
-- goods land in inventory as batches and stock, and selling a product
-- made from them charges the cost again through
-- RevenueService.recordCogs. Every Quick Add purchase was counted twice:
-- in full when entered, and again as its stock sold.
--
-- The code path is removed. Rows created by it after V140 ran are still
-- live and still inflating expenses, so soft-delete them the same way.
-- Matched on purchase_order_id, which createExpenseFromPurchaseOrder
-- always sets, with the V140 description backstop for older rows.
UPDATE financial_expenses
SET deleted_at = NOW(),
    deleted_by = 'V153_BACKFILL',
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND (
      purchase_order_id IS NOT NULL
      OR (category = 'INVENTORY' AND description LIKE 'Purchase Order:%')
  );
