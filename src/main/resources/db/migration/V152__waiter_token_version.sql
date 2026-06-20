-- V152: waiter token revocation support (Phase 0 §3.5).
--
-- Mirror of V149 (users) for waiters. A monotonically increasing per-waiter version embedded in
-- issued waiter tokens. Incrementing it (on PIN change or deactivation) instantly invalidates every
-- token the waiter already holds — JwtAuthenticationFilter rejects any waiter token whose tokenVersion
-- trails the stored one. Waiter access tokens are long-lived (30 days) and have no refresh flow, so
-- without this a changed/disabled PIN would keep working for up to a month.
--
-- Defaults to 0 so existing tokens (which carry no claim, treated as 0) stay valid until the first bump.
ALTER TABLE waiters ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
