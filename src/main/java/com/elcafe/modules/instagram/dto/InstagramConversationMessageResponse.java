package com.elcafe.modules.instagram.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * One line of a merged Instagram conversation transcript ({@code InstagramInboxService#getConversation})
 * — either an inbound {@code InstagramInboundMessage} (V179) or an outbound {@code InstagramLog} (V171)
 * row, normalised to a common shape so the two can be sorted together chronologically.
 */
@Data
@Builder
public class InstagramConversationMessageResponse {

    public enum Direction { IN, OUT }

    private Direction direction;
    private String text;
    private OffsetDateTime timestamp;

    /** Outbound only ({@code InstagramMessageType} name — AUTOMATION/MANUAL/CAMPAIGN/...); null for an
     *  inbound row, which has no such classification. */
    private String messageType;

    /** Outbound only ({@code MessageStatus} name — SENT/FAILED/...); null for an inbound row, which was
     *  received, not sent, so "delivery status" does not apply to it. */
    private String status;
}
