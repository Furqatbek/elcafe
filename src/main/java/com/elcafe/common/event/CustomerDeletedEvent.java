package com.elcafe.common.event;

/**
 * Published by {@code CustomerService.deleteCustomer} just before the customer row is removed, so
 * channel modules can erase the PII they hold for that person. It decouples the core customer module
 * from Instagram/Telegram (which depend on customer, not the reverse) and keeps the purge inside the
 * delete transaction: synchronous {@code @EventListener}s run before the customer is deleted, so a
 * failure rolls the whole thing back and no orphaned subscriber row is left behind.
 *
 * @param customerId the customer being deleted
 */
public record CustomerDeletedEvent(Long customerId) {
}
