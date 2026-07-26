package com.elcafe.modules.instagram.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * One row of the Instagram inbox's "recent conversations" listing ({@code
 * InstagramInboxService#listConversations}) — a subscriber with at least one inbound message, plus a
 * preview of the latest one. One subscriber is one conversation (see the inbox service's javadoc).
 */
@Data
@Builder
public class InstagramConversationSummaryResponse {
    private Long subscriberId;
    private String igsid;
    private String username;
    private String displayName;
    private String conversationState;
    private Boolean isBlocked;

    /** The most recent inbound message's text, or null on the (expected-rare) chance the newest row
     *  carries no text — e.g. a legacy/edge-case row. */
    private String preview;

    private OffsetDateTime lastMessageAt;

    /** Non-null AND in the future means a human agent currently owns this thread — see {@code
     *  InstagramSubscriber#getHumanHandoffUntil()}. */
    private OffsetDateTime humanHandoffUntil;
}
