-- One-time recovery for a bug in SalaryAutoPayService.processPayment where
-- a manual "Pay Now" on a DAILY / PER_SHIFT / HOURLY config silently
-- advanced salary_configs.last_paid_date without ever creating a
-- PayrollEntry (when the employee had no clocked shifts in the period).
-- That stuck the Pay Now button on disabled with no record to show for it.
--
-- The fix in code now throws and leaves the cursor alone, but rows already
-- corrupted by the old behavior need a hand. Clear last_paid_date on any
-- shift-driven config whose cursor isn't backed by an actual PAID payroll
-- entry within the last 60 days. MONTHLY/WEEKLY/BIWEEKLY configs always
-- write an entry on pay (computeBaseAmount never returns 0 for them) so
-- their cursors are trustworthy and left alone.
UPDATE salary_configs sc
SET last_paid_date = NULL
WHERE sc.last_paid_date IS NOT NULL
  AND sc.pay_frequency IN ('DAILY', 'PER_SHIFT', 'HOURLY')
  AND NOT EXISTS (
      SELECT 1
      FROM financial_payroll_entries pe
      WHERE pe.restaurant_id = sc.restaurant_id
        AND pe.deleted_at IS NULL
        AND pe.status = 'PAID'
        AND pe.payment_date BETWEEN sc.last_paid_date - INTERVAL '60 days'
                                AND sc.last_paid_date
        AND (
              (sc.employee_id IS NOT NULL AND pe.employee_id = sc.employee_id)
           OR (sc.waiter_id   IS NOT NULL AND pe.waiter_id   = sc.waiter_id)
        )
  );
