-- V182: welcome bonus for a guest who completes registration.
--
-- Two doors lead here and they verify differently, deliberately:
--   * Online QR menu — the guest registers themselves and proves the phone via the existing consumer
--     OTP. The grant fires in ConsumerAuthService.verifyOtp, i.e. only AFTER the code is accepted, so
--     requesting a code and walking away credits nothing.
--   * At the till — staff offer the bonus while taking payment and key in a name and phone. No OTP:
--     the employee standing with the guest is the trust anchor, and making a customer read back six
--     digits at a busy counter is how an offer quietly stops being made.
--
-- Because the second door credits real value on an unconfirmed number, the guard is visibility rather
-- than friction: customers.registered_by_user_id records WHICH employee did it, which is what makes
-- "registrations per employee per shift" answerable and the decision reversible if it is ever abused.
-- Idempotency itself is not enforced here but by BonusTransaction.idempotency_key ("registration-<id>",
-- one per customer forever), the same mechanism birthday/first-order/reactivation grants already use.

ALTER TABLE loyalty_config
    ADD COLUMN IF NOT EXISTS registration_bonus_amount  NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS registration_bonus_enabled BOOLEAN       NOT NULL DEFAULT false;

COMMENT ON COLUMN loyalty_config.registration_bonus_amount IS
    'Welcome bonus credited once when a guest completes registration. 0 disables it just as the flag does.';
COMMENT ON COLUMN loyalty_config.registration_bonus_enabled IS
    'Master switch for the welcome bonus. Off by default: this grants real money, so it stays dark until an operator turns it on.';

-- Nullable by design: only the staff-assisted door has an employee to record. A self-registered guest
-- leaves this NULL, which is itself the signal that the phone went through OTP.
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS registered_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

COMMENT ON COLUMN customers.registered_by_user_id IS
    'Employee who registered this customer at the till (staff-assisted, no OTP). NULL when the guest registered themselves online.';

-- Backs the per-employee registration counts described above.
CREATE INDEX IF NOT EXISTS idx_customers_registered_by
    ON customers(registered_by_user_id)
    WHERE registered_by_user_id IS NOT NULL;
