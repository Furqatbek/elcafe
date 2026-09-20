package com.elcafe.modules.partner.service;

import com.elcafe.modules.partner.dto.OwedTicketRow;
import com.elcafe.modules.partner.dto.OwedTicketsResponse;
import com.elcafe.modules.partner.repository.PartnerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tickets a venue made and nobody collected, added up.
 *
 * <p>A partner's customer cancels after the kitchen has started; we refuse, because the ingredients
 * are gone and the time is spent; their side closes the order and refunds anyway. Each of those is
 * already stamped on the order and shown beside its status, which is what lets staff close the ticket
 * on the day. This is the other half: the number somebody needs when the month ends.
 *
 * <p>It computes the totals from the rows rather than asking the database for both. These are rare by
 * construction and the list is short, so a second aggregate query would buy nothing and could disagree
 * with the list printed under it — which is the one thing a page about money must never do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwedTicketService {

    private final PartnerOrderRepository partnerOrderRepository;

    /**
     * @param from inclusive; defaults to the start of the current month, which is the period a venue
     *             actually reconciles
     * @param to   exclusive; defaults to now
     */
    @Transactional(readOnly = true)
    public OwedTicketsResponse forVenue(Long restaurantId, OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime start = from != null ? from : startOfThisMonth();
        OffsetDateTime end = to != null ? to : OffsetDateTime.now();

        List<OwedTicketRow> tickets = partnerOrderRepository.findOwedTickets(restaurantId, start, end);

        return OwedTicketsResponse.builder()
                .restaurantId(restaurantId)
                .from(start)
                .to(end)
                .ticketCount(tickets.size())
                .foodValueTotal(sum(tickets))
                .byPartner(perPartner(tickets))
                .tickets(tickets)
                .build();
    }

    private static OffsetDateTime startOfThisMonth() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static BigDecimal sum(List<OwedTicketRow> tickets) {
        return tickets.stream()
                .map(ticket -> ticket.foodValue() == null ? BigDecimal.ZERO : ticket.foodValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Split by partner, because a venue on two aggregators settles with them separately and a single
     * total would have to be taken apart by hand before it could be used for anything.
     */
    private static List<OwedTicketsResponse.PartnerTotal> perPartner(List<OwedTicketRow> tickets) {
        Map<String, List<OwedTicketRow>> byName = new LinkedHashMap<>();
        for (OwedTicketRow ticket : tickets) {
            byName.computeIfAbsent(ticket.partnerName(), name -> new ArrayList<>()).add(ticket);
        }

        List<OwedTicketsResponse.PartnerTotal> totals = new ArrayList<>();
        byName.forEach((name, rows) -> totals.add(OwedTicketsResponse.PartnerTotal.builder()
                .partnerName(name)
                .ticketCount(rows.size())
                .foodValueTotal(sum(rows))
                .build()));
        return totals;
    }
}
