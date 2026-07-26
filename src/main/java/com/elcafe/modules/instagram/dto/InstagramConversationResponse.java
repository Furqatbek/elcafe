package com.elcafe.modules.instagram.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One conversation's full detail ({@code InstagramInboxService#getConversation}): the subscriber
 * (including their current {@code humanHandoffUntil} status) and the merged, chronological transcript
 * of inbound (V179 {@code InstagramInboundMessage}) and outbound (V171 {@code InstagramLog}) messages.
 */
@Data
@Builder
public class InstagramConversationResponse {
    private InstagramSubscriberResponse subscriber;
    private List<InstagramConversationMessageResponse> messages;
}
