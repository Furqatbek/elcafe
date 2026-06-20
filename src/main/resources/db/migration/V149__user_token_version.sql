-- V149: token revocation support (Phase 0 §3.5).
--
-- A monotonically increasing per-user version embedded in issued tokens. Incrementing it (on
-- password change/reset, "log out everywhere", or account suspension) instantly invalidates every
-- token the user already holds — JwtUtil#validateToken rejects any token whose tokenVersion differs
-- from the stored one. Defaults to 0 so existing tokens (which carry no claim, treated as 0) stay
-- valid until the first bump.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
