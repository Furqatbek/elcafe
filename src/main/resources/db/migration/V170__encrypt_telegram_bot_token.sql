-- V170: encrypt the Telegram bot token at rest, preserving "one bot per token".
--
-- bot_token grants full control of a restaurant's bot and is the tenant key for every inbound update;
-- it was stored in plaintext. It is now encrypted via EncryptedStringConverter (see V169). Two problems
-- follow from encrypting it:
--   1) the ciphertext envelope is larger than VARCHAR(100)  → widen the column to TEXT;
--   2) encryption is non-deterministic (random IV per value), so the existing UNIQUE index on bot_token
--      can no longer catch two restaurants registering the SAME bot → add a blind-index column,
--      bot_token_hash (a deterministic HMAC of the token, maintained by the entity), and enforce
--      uniqueness on it.
--
-- The original uq_tg_config_bot_token index is kept: it still enforces uniqueness in the inert (no-key)
-- case where bot_token stays plaintext, and is harmless once values are ciphertext. NULLs are distinct
-- in a SQL unique index, so legacy rows — plaintext, null hash until their next save — coexist without a
-- WHERE clause.

ALTER TABLE telegram_bot_config ALTER COLUMN bot_token TYPE TEXT;
ALTER TABLE telegram_bot_config ADD COLUMN bot_token_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_tg_config_bot_token_hash
    ON telegram_bot_config(bot_token_hash);
