package com.elcafe.modules.instagram.enums;

/**
 * Which of a restaurant's own Instagram subscribers a campaign targets. Mirrors the two audiences the
 * old {@code broadcast(text, target)} accepted, now as a typed value.
 */
public enum InstagramCampaignAudience {
    /** All active, non-blocked subscribers. */
    ALL,
    /** Only fully-registered subscribers (conversation_state = REGISTERED). */
    REGISTERED
}
