package com.elcafe.modules.order.scheduler;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

                        // TODO: Send SMS notification to customer
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

                        // TODO: Send SMS notification to customer
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

            // TODO: Store metrics in cache (Redis) for analytics endpoint
            // TODO: Calculate revenue totals
            // TODO: Calculate average order value
            // TODO: Identify top products

            log.info("Order metrics calculation completed");
        } catch (Exception e) {
            log.error("Order metrics calculation failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up old data and expired cache entries.
     * Runs every 6 hours.
     */
    @Scheduled(cron = "0 0 */6 * * *") // Every 6 hours
    @SchedulerLock(name = "order-data-cleanup", lockAtLeastFor = "PT30S")
    public void cleanupOldData() {
        try {
            log.info("Starting cleanup job");

            // Archive old order events (older than 90 days)
            OffsetDateTime ninetyDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90);

            // TODO: Archive or delete old order_events records
            // TODO: Clean up expired Redis cache keys
            // TODO: Clean up old session data

            log.info("Cleanup job completed");
        } catch (Exception e) {
            log.error("Cleanup job failed: {}", e.getMessage(), e);
        }
    }
}
