package com.elcafe.modules.financial.service;

import com.elcafe.modules.notification.service.FinancialOperationAlertService;
import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service for recording revenue with retry and alerting capabilities.
 * Provides resilient revenue recording that:
 * - Retries on transient failures (up to 3 attempts with exponential backoff)
 * - Sends alerts to restaurant admins on final failure
 * - Ensures payment transactions are not blocked by revenue recording failures
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueRecordingService {

    private final RevenueService revenueService;
    private final FinancialOperationAlertService alertService;

    /**
     * Record revenue for an order with retry support.
     * This method is async to not block the main payment flow.
     *
     * <p>CONTRACT: runs on an @Async thread with a DETACHED entity and no session (open-in-view is
     * off), so the caller must pass an order whose lazily-read state is already initialised —
     * payments (picked apart by {@code pickRevenueDestinationAccount}) and items (iterated by the
     * COGS recorder). See {@code PaymentService#recordRevenueNonCritical}.
     *
     * @param order The order to record revenue for
     */
    @Async
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000)
    )
    public void recordRevenueWithRetry(Order order) {
        Long orderId = order.getId();
        log.info("Recording revenue for order {} (attempt may be retried)", orderId);

        try {
            revenueService.recordOrderRevenue(order);
            log.info("Revenue successfully recorded for order {}", orderId);
        } catch (Exception e) {
            log.warn("Revenue recording failed for order {}, will retry: {}", orderId, e.getMessage());
            throw e; // Re-throw to trigger retry
        }
    }

    /**
     * Recovery method called after all retry attempts are exhausted.
     * Sends alert to restaurant admins about the failure.
     */
    @Recover
    public void handleRevenueRecordingFailure(Exception e, Order order) {
        Long orderId = order.getId();
        String orderNumber = order.getOrderNumber();
        Long restaurantId = order.getRestaurant() != null ? order.getRestaurant().getId() : null;
        BigDecimal amount = order.getTotal();

        log.error("Revenue recording permanently failed for order {} after retries: {}",
                  orderId, e.getMessage());

        // Send alert to restaurant admins
        alertService.alertRevenueRecordingFailure(
                orderId,
                orderNumber,
                restaurantId,
                amount,
                e.getMessage(),
                3 // Final attempt number
        );

        // Log for manual reconciliation
        log.error("MANUAL_RECONCILIATION_REQUIRED: Order {} (#{}) amount {} - revenue not recorded. Error: {}",
                  orderId, orderNumber, amount, e.getMessage());
    }

    /**
     * Synchronous revenue recording with immediate alerting on failure.
     * Use this for critical paths where async is not suitable.
     *
     * @param order The order to record revenue for
     * @return true if successful, false otherwise
     */
    public boolean recordRevenueSynchronously(Order order) {
        try {
            revenueService.recordOrderRevenue(order);
            log.info("Revenue recorded synchronously for order {}", order.getId());
            return true;
        } catch (Exception e) {
            log.error("Synchronous revenue recording failed for order {}: {}",
                      order.getId(), e.getMessage());

            // Alert immediately
            alertService.alertRevenueRecordingFailure(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getRestaurant() != null ? order.getRestaurant().getId() : null,
                    order.getTotal(),
                    e.getMessage(),
                    1
            );
            return false;
        }
    }
}
