package com.elcafe.modules.instagram.enums;

/**
 * What kind of send an {@link com.elcafe.modules.instagram.entity.InstagramLog} row records.
 *
 * <p>Mirrors {@link com.elcafe.modules.telegram.enums.TelegramMessageType}, with one Instagram-only
 * addition: {@link #AUTO_REPLY}. Telegram has no public "comment" concept, so that source has nothing
 * to mirror on the Telegram side.
 */
public enum InstagramMessageType {
    /** Sent by the DM registration wizard ({@code InstagramBotService.dispatch}) — automated, not admin-typed. */
    AUTOMATION,

    /** Sent as part of a broadcast campaign ({@code InstagramCampaignExecutor}). */
    CAMPAIGN,

    /** Manually sent by an admin to one subscriber ({@code InstagramBotService.sendAdminMessage}). */
    MANUAL,

    /** System notifications (order status, etc.) — reserved for future non-wizard automated sends. */
    NOTIFICATION,

    /** Automatic public reply to a comment on a business post ({@code InstagramWebhookService}). */
    AUTO_REPLY
}
