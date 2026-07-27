package com.elcafe.modules.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One line of a guest's conversation history (V183), normalised from whichever channel it came from so
 * Instagram, Telegram and SMS can be sorted into a single stream.
 *
 * <p>The underlying tables disagree about almost everything — they key on a subscriber, a phone or a
 * customer, and store {@code OffsetDateTime} in one place and {@code LocalDateTime} in another. That is
 * all flattened here; the merge is only sane because every row ends up with one comparable timestamp.
 *
 * <p><b>Known asymmetry, not a bug.</b> Only Instagram stores what the guest SENT us
 * ({@code instagram_inbound_message}, V179). Telegram and SMS keep send logs only, so their side of the
 * conversation is everything we said and nothing they replied. {@link #direction} makes that visible
 * rather than letting a one-sided thread look like a complete one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTimelineEntry {

    public enum Channel { INSTAGRAM, TELEGRAM, SMS }

    public enum Direction {
        /** The guest sent this to us. Instagram only, today. */
        IN,
        /** We sent this to the guest. */
        OUT
    }

    private Channel channel;

    private Direction direction;

    private String text;

    /** The single comparable instant every source is normalised onto — and the paging cursor. */
    private OffsetDateTime timestamp;

    /** Outbound only: the channel's own message classification (CAMPAIGN, AUTOMATION, MANUAL…). */
    private String messageType;

    /** Outbound only: delivery status (SENT, DELIVERED, FAILED…). Null inbound — it was received. */
    private String status;
}
