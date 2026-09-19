package com.elcafe.modules.partner.dto;

import com.elcafe.modules.partner.enums.PartnerOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** What a partner tells us when the order they sent moved on their side. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerOrderStatusUpdateRequest {

    @NotNull(message = "status is required")
    @Schema(description = "The state in your own vocabulary. CREATED and REFUNDED are accepted and "
            + "acknowledged but move nothing here.", example = "ACCEPTED")
    private PartnerOrderStatus status;

    @Size(max = 500)
    @Schema(description = "Why, when it is a cancellation or rejection. Shown to staff and kept on the "
            + "order, so 'kitchen closed' is worth more than 'cancelled'.",
            example = "Restaurant declined — closing early")
    private String reason;

    /**
     * When it happened on their side, not when we heard about it.
     *
     * <p>Recorded rather than acted on. Delivery is at-least-once and retries can arrive minutes
     * late, so the gap between this and our own timestamp is the only way to tell a slow webhook from
     * a slow kitchen when someone is reading the history afterwards.
     */
    @Schema(description = "ISO-8601 instant the change happened on your side.",
            example = "2026-09-19T14:32:00+05:00")
    private OffsetDateTime occurredAt;
}
