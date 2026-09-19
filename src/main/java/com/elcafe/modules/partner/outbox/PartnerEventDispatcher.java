package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.entity.Partner;

/**
 * Delivers one outbound message to one partner's API.
 *
 * <p>This is the only place a partner's protocol lives. The outbox itself knows nothing about ZBR,
 * about HTTP, or about what a menu update looks like on the wire — it knows how to durably hold a
 * message, retry it, and give up on it. Everything specific to a partner goes in an implementation of
 * this interface, so onboarding the next aggregator is a new bean rather than a change to the queue.
 *
 * <p><b>Implementations must be idempotent from the partner's side.</b> The outbox guarantees
 * at-least-once delivery, not exactly-once: an attempt that times out after the partner processed it
 * will be retried. Send a stable id the partner can dedupe on.
 *
 * <p>Throwing signals a failure the outbox should retry. Returning normally means delivered.
 */
public interface PartnerEventDispatcher {

    /**
     * Whether this dispatcher handles that partner — typically a slug check.
     *
     * <p>A partner with no dispatcher never has events enqueued at all, so the table does not fill with
     * messages nobody can deliver. That is why the publisher asks this before writing anything.
     */
    boolean supports(Partner partner);

    /**
     * @throws Exception any failure; the outbox records it, backs off and retries until the attempts
     *                   are spent, then dead-letters the event for an operator to see.
     */
    void dispatch(Partner partner, IntegrationEvent event) throws Exception;
}
