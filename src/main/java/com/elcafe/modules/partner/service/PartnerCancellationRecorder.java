package com.elcafe.modules.partner.service;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Writes down the ticket a venue is owed for, at the moment we refuse to cancel it.
 *
 * <p>A partner's customer cancels after the kitchen has started. The partner refunds them, marks
 * their order cancelled and tells us; we answer {@code 422 CANCELLATION_WINDOW_CLOSED}. Both systems
 * are then right and they disagree — their order is cancelled, ours is still being cooked, and no
 * courier is coming for it. The refusal is the correct answer and we are keeping it, but on its own
 * it left nothing behind: the venue absorbed a meal and by close of books nobody could say which
 * ticket it had been.
 *
 * <p><b>REQUIRES_NEW, and that is the whole design.</b> The refusal throws, and the throw rolls the
 * caller's transaction back — so a record written inside it would be rolled back with it. This runs
 * in its own transaction, commits before the exception is raised, and survives.
 *
 * <p>It is deliberately not a settlement. Who bears the cost is the commercial question neither
 * company has answered, and an answer reached next month can only be applied to these tickets if
 * they were written down as they happened.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerCancellationRecorder {

    /** Matches {@code orders.partner_cancel_refused_reason}. */
    private static final int MAX_REASON = 500;

    private final OrderRepository orderRepository;

    /**
     * Stamps the order, unless it already carries a refusal.
     *
     * @return true if this call wrote the record, false if one was already there
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordRefusal(Long orderId, String partnerSlug, OrderStatus stage, String reason) {
        int stamped = orderRepository.recordPartnerCancelRefused(
                orderId, OffsetDateTime.now(), stage, truncate(reason));

        if (stamped == 0) {
            // A redelivered webhook, or the customer pressing cancel twice. The venue is owed for one
            // ticket, not two, and the first refusal is the one that describes how far the food got.
            log.debug("Partner {} re-reported a refused cancellation for order {} — already recorded",
                    partnerSlug, orderId);
            return false;
        }

        log.warn("Partner {} cancelled order {} after we refused at {} — the venue is owed for this "
                        + "ticket. Reason: {}",
                partnerSlug, orderId, stage, reason);
        return true;
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON);
    }
}
