package com.elcafe.modules.order.scheduler;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Background jobs for order management.
 * Based on CLIENT_RESTAURANT_FLOW.md documentation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBackgroundJobs {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderEventRepository orderEventRepository;

    /**
     * Order-event retention is a data-removing job, so it defaults OFF (dark-launch pattern).
     * Flip {@code ORDER_EVENT_RETENTION_ENABLED=true} once an operator has confirmed the desired
     * horizon; {@code ORDER_EVENT_RETENTION_DAYS} controls how far back to keep (default 90 days).
     */
    @Value("${app.order.event-retention.enabled:false}")
    private boolean eventRetentionEnabled;

    @Value("${app.order.event-retention.days:90}")
    private int eventRetentionDays;

    /**
     * Auto-reject orders that haven't been accepted within 10 minutes.
     * Runs every minute.
     *
     * Business Rule: Orders in PLACED status for more than 10 minutes
     * are automatically rejected and refunded.
     */
    @Scheduled(cron = "0 * * * * *") // Every minute
    @SchedulerLock(name = "order-auto-reject-expired", lockAtLeastFor = "PT30S")
    @Transactional
    public void autoRejectExpiredOrders() {
        try {
            OffsetDateTime tenMinutesAgo = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);

            // Find orders that are still PLACED after 10 minutes
            List<Order> expiredOrders = orderRepository
                    .findByStatusAndPlacedAtBefore(OrderStatus.PLACED, tenMinutesAgo);

            if (!expiredOrders.isEmpty()) {
                log.info("Found {} expired orders to auto-reject", expiredOrders.size());

                for (Order order : expiredOrders) {
                    try {
                        log.info("Auto-rejecting order {} placed at {}",
                                order.getOrderNumber(), order.getPlacedAt());

                        orderService.rejectOrder(
                                order.getId(),
                                "Order automatically rejected - not accepted within 10 minutes",
                                "SYSTEM"
                        );

                        log.info("Successfully auto-rejected order: {}", order.getOrderNumber());

                        // Customer and owners are already notified: rejectOrder -> updateOrderStatus
                        // (CANCELLED) fans out via CustomerNotificationService + OwnerNotificationService.
                    } catch (Exception e) {
                        log.error("Failed to auto-reject order {}: {}",
                                order.getOrderNumber(), e.getMessage(), e);
                    }
                }

                log.info("Auto-rejection job completed. Rejected {} orders", expiredOrders.size());
            }
        } catch (Exception e) {
            log.error("Auto-rejection job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Verify pending payments and cancel orders with failed payments.
     * Runs every 5 minutes.
     *
     * Business Rule: Orders in PENDING status for more than 15 minutes
     * are automatically cancelled.
     */
    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
    @SchedulerLock(name = "order-verify-pending-payments", lockAtLeastFor = "PT30S")
    @Transactional
    public void verifyPendingPayments() {
        try {
            OffsetDateTime fifteenMinutesAgo = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15);

            // Find orders stuck in PENDING status
            List<Order> pendingOrders = orderRepository
                    .findByStatusAndCreatedAtBefore(OrderStatus.PENDING, fifteenMinutesAgo);

            if (!pendingOrders.isEmpty()) {
                log.info("Found {} pending orders to verify", pendingOrders.size());

                for (Order order : pendingOrders) {
                    try {
                        log.info("Verifying payment for order {} created at {}",
                                order.getOrderNumber(), order.getCreatedAt());

                        // TODO: Check payment status with payment gateway
                        // For now, auto-cancel orders with PENDING status after 15 minutes

                        orderService.cancelOrder(
                                order.getId(),
                                "Payment not completed within 15 minutes",
                                "SYSTEM"
                        );

                        log.info("Cancelled order with failed payment: {}", order.getOrderNumber());

                        // Customer and owners are already notified: cancelOrder -> updateOrderStatus
                        // (CANCELLED) fans out via CustomerNotificationService + OwnerNotificationService.
                    } catch (Exception e) {
                        log.error("Failed to cancel order {}: {}",
                                order.getOrderNumber(), e.getMessage(), e);
                    }
                }

                log.info("Payment verification job completed. Cancelled {} orders", pendingOrders.size());
            }
        } catch (Exception e) {
            log.error("Payment verification job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Calculate and cache order metrics.
     * Runs every hour.
     *
     * Calculates:
     * - Daily order counts
     * - Revenue totals
     * - Average order values
     * - Popular products
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @SchedulerLock(name = "order-metrics-hourly", lockAtLeastFor = "PT30S")
    public void calculateOrderMetrics() {
        try {
            log.info("Starting order metrics calculation job");

            OffsetDateTime startOfDay = OffsetDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            // Count in the database — this job only logs 3 numbers, so materializing every order of the
            // day (entities + associations), hourly, was pure heap burn (audit PERF-8).
            long totalOrders = orderRepository.countByCreatedAtBetween(startOfDay, now);
            long completedOrders = orderRepository.countByStatusAndCreatedAtBetween(
                    OrderStatus.COMPLETED, startOfDay, now);
            long cancelledOrders = orderRepository.countByStatusAndCreatedAtBetween(
                    OrderStatus.CANCELLED, startOfDay, now);

            log.info("Order metrics - Total: {}, Completed: {}, Cancelled: {}",
                    totalOrders, completedOrders, cancelledOrders);

            // Revenue totals, average order value and top products are served on demand by the
            // analytics module (FinancialAnalyticsService / OperationalAnalyticsService) straight
            // from SQL aggregates, so there is no precomputed cache to warm here. This job stays a
            // lightweight heartbeat that logs the day's headline counts.

            log.info("Order metrics calculation completed");
        } catch (Exception e) {
            log.error("Order metrics calculation failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Prune the order-event audit log to bound table growth.
     * Runs every 6 hours.
     *
     * <p>Disabled by default (dark-launch): the delete is skipped unless
     * {@code app.order.event-retention.enabled=true}. When enabled, rows older than
     * {@code app.order.event-retention.days} (default 90) are bulk-deleted. Redis cache keys and
     * session data self-expire via their own TTLs, so nothing to sweep here.
     */
    @Scheduled(cron = "0 0 */6 * * *") // Every 6 hours
    @SchedulerLock(name = "order-data-cleanup", lockAtLeastFor = "PT30S")
    @Transactional
    public void cleanupOldData() {
        try {
            log.info("Starting cleanup job");

            if (!eventRetentionEnabled || eventRetentionDays <= 0) {
                log.debug("Order-event retention disabled (enabled={}, days={}); skipping cleanup",
                        eventRetentionEnabled, eventRetentionDays);
                return;
            }

            LocalDateTime cutoff = LocalDateTime.now().minusDays(eventRetentionDays);
            int deleted = orderEventRepository.deleteOlderThan(cutoff);
            log.info("Order-event retention: deleted {} events older than {} ({} days)",
                    deleted, cutoff, eventRetentionDays);

            log.info("Cleanup job completed");
        } catch (Exception e) {
            log.error("Cleanup job failed: {}", e.getMessage(), e);
        }
    }
}
