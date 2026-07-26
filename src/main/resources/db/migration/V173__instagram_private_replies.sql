-- V173: Instagram private replies from comments — the "comment MENU and we'll DM you" growth loop.
--
-- Meta's private-reply endpoint (POST /{ig-user-id}/messages with recipient.comment_id) opens a fresh
-- 24-hour DM messaging window with whoever left a matching comment. InstagramWebhookService.
-- processChangeEvent matches the comment text against private_reply_keyword and, when
-- private_reply_enabled, sends private_reply_template as a DM — independent of (and in addition to)
-- the existing public auto_reply_* columns from V105. When private_reply_promotion_id is set, {code}
-- in the template is substituted with a coupon minted via CouponService; otherwise (or if minting
-- fails) {code} is stripped and the DM still goes out.
--
-- private_reply_promotion_id gets an FK with ON DELETE SET NULL, matching
-- instagram_subscribers.customer_id (V105) rather than instagram_logs.campaign_id's plain-BIGINT,
-- no-FK convention (V171): campaign_id is always loaded and passed around as a bare id by the
-- campaign executor and never re-dereferenced, whereas this column is looked up fresh on every
-- matching comment, so a promotion deleted out from under a bot config should cleanly clear the
-- pointer rather than leave a stale id that could later collide with an unrelated promotion reusing
-- the same numeric id.
ALTER TABLE instagram_bot_config ADD COLUMN IF NOT EXISTS private_reply_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE instagram_bot_config ADD COLUMN IF NOT EXISTS private_reply_keyword VARCHAR(100);
ALTER TABLE instagram_bot_config ADD COLUMN IF NOT EXISTS private_reply_template TEXT;
ALTER TABLE instagram_bot_config ADD COLUMN IF NOT EXISTS private_reply_promotion_id BIGINT
    REFERENCES promotions(id) ON DELETE SET NULL;
