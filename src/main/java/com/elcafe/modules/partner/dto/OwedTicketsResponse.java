package com.elcafe.modules.partner.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * What a venue is owed for, over a period, per partner and in total.
 *
 * <p>Deliberately the venue's own number rather than one their aggregator quotes them. A restaurant
 * told by a delivery platform what that platform owes them has no way to check it, which is the
 * position this whole integration has been trying to get both sides out of. The same rows exist on
 * the partner's side; if the two disagree, one of us has a bug, and the cheapest time to find that is
 * while the numbers are small.
 */
@Data
@Builder
public class OwedTicketsResponse {

    private Long restaurantId;
    private OffsetDateTime from;
    private OffsetDateTime to;

    /** How many tickets in the period. Zero is the normal answer and is worth showing as zero. */
    private int ticketCount;

    /** Sum of {@link OwedTicketRow#foodValue()} across the period. What the food was worth, not a debt. */
    private BigDecimal foodValueTotal;

    /** Broken down by partner, because a venue on two aggregators settles with them separately. */
    private List<PartnerTotal> byPartner;

    private List<OwedTicketRow> tickets;

    @Data
    @Builder
    public static class PartnerTotal {
        private String partnerName;
        private int ticketCount;
        private BigDecimal foodValueTotal;
    }
}
