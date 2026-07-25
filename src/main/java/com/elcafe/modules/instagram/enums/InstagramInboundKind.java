package com.elcafe.modules.instagram.enums;

/**
 * What an inbound Instagram messaging event actually IS.
 *
 * <p>The webhook used to look for a {@code text} field and nothing else, then call the registration
 * wizard with it. Everything that was not plain text either fell through silently or was fed into the
 * wizard as if the user had typed it — so a photo left the customer parked mid-wizard with no reply,
 * and a "🔥" reply to a story was persisted as a delivery address. Classifying the event once, before
 * dispatch, is what lets each of those get its own answer.
 *
 * <p>Only the kinds the bot can act on appear here. Echoes, read/delivery receipts and reactions are
 * dropped in the webhook and never reach the bot at all.
 */
public enum InstagramInboundKind {

    /** A normal typed message. The only kind the registration wizard consumes as input. */
    TEXT,

    /** A quick-reply bubble or persistent-menu postback: carries a payload, not free text. */
    QUICK_REPLY,

    /**
     * A reply to one of the business account's stories. High-volume for a restaurant account and
     * almost never wizard input — usually an emoji reaction to a photo.
     */
    STORY_REPLY,

    /** The business account was mentioned in someone's story. The warmest possible lead. */
    STORY_MENTION,

    /**
     * Media the bot cannot interpret: photo, video, voice note, sticker, file, location or a shared
     * post. The wizard should re-prompt for whatever it was waiting for rather than stay silent.
     */
    UNSUPPORTED_ATTACHMENT
}
