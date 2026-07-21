-- Align the bonus_transactions.check_transaction_type CHECK with the current
-- BonusTransaction.TransactionType enum.
--
-- V27 shipped the constraint with the original 10 values. The enum has since gained two more:
--   * REFERRAL_BONUS  -- referral-program credit
--   * TOP_UP          -- customer-funded wallet top-up (Click / Payme / cash-at-counter)
-- Writing either row would violate the old CHECK on PostgreSQL (H2 tests miss it because Flyway is
-- disabled there and the schema comes from ddl-auto). Recreate the constraint with the full set so
-- those transaction types can be persisted. Both names fit the existing VARCHAR(30) column, so no
-- column change is needed. Idempotent (DROP IF EXISTS) and safe on the always-empty deploy DB.

ALTER TABLE bonus_transactions DROP CONSTRAINT IF EXISTS check_transaction_type;

ALTER TABLE bonus_transactions ADD CONSTRAINT check_transaction_type CHECK (transaction_type IN (
    'EARNED', 'SPENT', 'REFUNDED', 'EXPIRED', 'ADJUSTMENT',
    'BIRTHDAY_BONUS', 'FIRST_ORDER_BONUS', 'REACTIVATION_BONUS',
    'PROMOTION_BONUS', 'ADMIN_ADJUSTMENT', 'REFERRAL_BONUS', 'TOP_UP'
));
