-- V169: widen credential columns that are now encrypted at rest.
--
-- Instagram app_secret / access_token and the owner Telegram bot_token grant posting/messaging control
-- of the merchant's account; a DB dump previously handed them over in plaintext. They are now encrypted
-- via EncryptedStringConverter (AES-256-GCM, keyed from ELCAFE_ENCRYPTION_KEY). The ciphertext envelope
-- (12-byte IV + ciphertext + 16-byte GCM tag, base64, plus an "enc:v1:" marker) is larger than the
-- plaintext, so the bounded columns are widened to TEXT. access_token was already TEXT.
--
-- Encryption is inert until a key is provisioned, and reads legacy plaintext transparently, so this
-- migration needs no data backfill: existing rows stay readable and are re-encrypted on their next save.

ALTER TABLE instagram_bot_config       ALTER COLUMN app_secret TYPE TEXT;
ALTER TABLE owner_telegram_bot_config  ALTER COLUMN bot_token  TYPE TEXT;
