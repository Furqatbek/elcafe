package com.elcafe.modules.instagram.enums;

/**
 * What condition an {@link com.elcafe.modules.instagram.entity.InstagramAutomationRule} fires on.
 * Mirrors {@link com.elcafe.modules.telegram.enums.TelegramTriggerType}, but deliberately narrowed to
 * only the two triggers {@link com.elcafe.modules.instagram.scheduler.InstagramScheduler} actually
 * implements — unlike Telegram's enum (which also carries {@code BOT_START}, {@code NEW_SUBSCRIBER},
 * {@code REFERRAL_REWARD}, {@code ORDER_STATUS}, {@code LOYALTY_MILESTONE} with no scheduler or event
 * hook wired to any of them on the Telegram side either), every value here has a real, working job
 * behind it. Telegram has no {@code WELCOME} trigger to mirror, so none is added here.
 */
public enum InstagramTriggerType {

    /**
     * The subscriber's recorded {@code birthDate} (month + day) is today. Resolved directly from
     * {@link com.elcafe.modules.instagram.entity.InstagramSubscriber#getBirthDate()} — the value the
     * registration wizard's AWAITING_BIRTHDAY step collects — rather than a linked {@code Customer}, so
     * a subscriber does not need any order/customer history for a birthday rule to reach them. This is
     * what fulfils the "🎁 you'll get birthday gifts" promise the wizard makes.
     */
    BIRTHDAY("Subscriber birthday"),

    /**
     * The subscriber has gone quiet: {@code lastInteractionAt} older than a configurable threshold
     * ({@code conditions.days_inactive}, default 14 — see {@code InstagramScheduler}). Named
     * {@code WIN_BACK} rather than Telegram's {@code INACTIVE_USER} / SMS's {@code INACTIVE_CUSTOMER}
     * because that is the product framing this feature ships under; the underlying targeting concept —
     * "message someone who stopped engaging" — is the same one those two siblings already use.
     *
     * <p>Read {@code InstagramScheduler}'s class javadoc before assuming a low sent-count here is a
     * bug: this trigger's entire audience is, by definition, subscribers who have gone quiet, which on
     * Instagram usually also means they are outside Meta's 24-hour DM window — an inherent platform
     * constraint, not a defect in this rule.
     */
    WIN_BACK("Subscriber has gone quiet (win-back)");

    private final String description;

    InstagramTriggerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
