package com.elcafe.common.security.service;

import com.elcafe.common.audit.entity.AuditAction;
import com.elcafe.common.audit.entity.AuditLog;
import com.elcafe.common.audit.service.AuditService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security service for financial operations (refunds, voids).
 * Enforces role-based access control and business rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialOperationSecurityService {

    private final AuditService auditService;

    // Configurable thresholds
    @Value("${security.refund.max-amount-without-approval:100.00}")
    private BigDecimal refundApprovalThreshold;

    @Value("${security.refund.rate-limit-per-hour:10}")
    private int refundRateLimitPerHour;

    @Value("${security.void.max-minutes-after-creation:30}")
    private int voidTimeWindowMinutes;

    @Value("${security.void.max-amount-without-approval:50.00}")
    private BigDecimal voidApprovalThreshold;

    // Roles that can perform various operations
    private static final Set<String> REFUND_ALLOWED_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_OWNER", "ROLE_MANAGER", "ROLE_CASHIER"
    );

    private static final Set<String> REFUND_ANY_AMOUNT_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_OWNER", "ROLE_MANAGER"
    );

    private static final Set<String> VOID_ALLOWED_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_OWNER", "ROLE_MANAGER"
    );

    private static final Set<String> VOID_WITHOUT_APPROVAL_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_OWNER"
    );

    /**
     * Check if the current user can perform a refund
     */
    public RefundAuthorizationResult canPerformRefund(Order order, BigDecimal refundAmount) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return RefundAuthorizationResult.denied("User not authenticated");
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        Set<String> userRoles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // Check if user has any refund-allowed role
        boolean hasRefundRole = userRoles.stream().anyMatch(REFUND_ALLOWED_ROLES::contains);
        if (!hasRefundRole) {
            auditService.logDenied(
                    AuditAction.REFUND_DENIED,
                    "Order",
                    order.getId(),
                    order.getRestaurant().getId(),
                    "User lacks refund permission. Roles: " + userRoles
            );
            return RefundAuthorizationResult.denied("Insufficient permissions to perform refunds");
        }

        // Check if user can refund any amount or needs approval
        boolean canRefundAnyAmount = userRoles.stream().anyMatch(REFUND_ANY_AMOUNT_ROLES::contains);

        if (!canRefundAnyAmount && refundAmount.compareTo(refundApprovalThreshold) > 0) {
            auditService.logPendingApproval(
                    AuditAction.REFUND_REQUESTED,
                    order.getId(),
                    order.getOrderNumber(),
                    order.getRestaurant().getId(),
                    refundAmount,
                    "Refund amount $" + refundAmount + " exceeds threshold $" + refundApprovalThreshold
            );
            return RefundAuthorizationResult.needsApproval(
                    "Refund amount exceeds threshold. Manager approval required.");
        }

        return RefundAuthorizationResult.permit();
    }

    /**
     * Check if the current user can void an order
     */
    public VoidAuthorizationResult canVoidOrder(Order order) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return VoidAuthorizationResult.denied("User not authenticated");
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        Set<String> userRoles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        Long restaurantId = order.getRestaurant().getId();

        // Check if user has void permission
        boolean hasVoidRole = userRoles.stream().anyMatch(VOID_ALLOWED_ROLES::contains);
        if (!hasVoidRole) {
            auditService.logDenied(
                    AuditAction.VOID_DENIED,
                    "Order",
                    order.getId(),
                    restaurantId,
                    "User lacks void permission. Roles: " + userRoles
            );
            return VoidAuthorizationResult.denied("Insufficient permissions to void orders");
        }

        // Check time window
        if (order.getCreatedAt() != null) {
            long minutesSinceCreation = Duration.between(
                    order.getCreatedAt(),
                    OffsetDateTime.now()
            ).toMinutes();

            if (minutesSinceCreation > voidTimeWindowMinutes) {
                boolean canOverrideTimeLimit = userRoles.stream()
                        .anyMatch(VOID_WITHOUT_APPROVAL_ROLES::contains);

                if (!canOverrideTimeLimit) {
                    auditService.logDenied(
                            AuditAction.VOID_DENIED,
                            "Order",
                            order.getId(),
                            restaurantId,
                            "Order is " + minutesSinceCreation + " minutes old. Void window is " +
                                    voidTimeWindowMinutes + " minutes."
                    );
                    return VoidAuthorizationResult.denied(
                            "Order cannot be voided after " + voidTimeWindowMinutes +
                                    " minutes. Contact a manager.");
                }
            }
        }

        // Check for card payments - cannot void, must refund
        boolean hasCardPayment = order.getPayments() != null && order.getPayments().stream()
                .anyMatch(this::isCardPayment);

        if (hasCardPayment) {
            auditService.logDenied(
                    AuditAction.VOID_DENIED,
                    "Order",
                    order.getId(),
                    restaurantId,
                    "Order has card payments. Must use refund instead of void."
            );
            return VoidAuthorizationResult.denied(
                    "Cannot void order with card payments. Use refund instead.");
        }

        // Check amount threshold
        BigDecimal orderTotal = order.getTotal();
        boolean canVoidWithoutApproval = userRoles.stream()
                .anyMatch(VOID_WITHOUT_APPROVAL_ROLES::contains);

        if (!canVoidWithoutApproval && orderTotal != null &&
                orderTotal.compareTo(voidApprovalThreshold) > 0) {
            auditService.logPendingApproval(
                    AuditAction.ORDER_VOID_REQUESTED,
                    order.getId(),
                    order.getOrderNumber(),
                    restaurantId,
                    orderTotal,
                    "Void amount $" + orderTotal + " exceeds threshold $" + voidApprovalThreshold
            );
            return VoidAuthorizationResult.needsApproval(
                    "Order total exceeds void threshold. Manager approval required.");
        }

        return VoidAuthorizationResult.permit();
    }

    /**
     * Approve a pending refund (manager action)
     */
    public void approveRefund(Long orderId, Long restaurantId, String reason) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        auditService.logAuthorization(
                AuditAction.REFUND_APPROVED,
                orderId,
                restaurantId,
                null, // Would need to extract from auth
                auth.getName(),
                reason
        );
    }

    /**
     * Reject a pending refund (manager action)
     */
    public void rejectRefund(Long orderId, Long restaurantId, String reason) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        auditService.logAuthorization(
                AuditAction.REFUND_REJECTED,
                orderId,
                restaurantId,
                null,
                auth.getName(),
                reason
        );
    }

    /**
     * Approve a pending void (manager action)
     */
    public void approveVoid(Long orderId, Long restaurantId, String reason) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        auditService.logAuthorization(
                AuditAction.ORDER_VOID_APPROVED,
                orderId,
                restaurantId,
                null,
                auth.getName(),
                reason
        );
    }

    /**
     * Reject a pending void (manager action)
     */
    public void rejectVoid(Long orderId, Long restaurantId, String reason) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        auditService.logAuthorization(
                AuditAction.ORDER_VOID_REJECTED,
                orderId,
                restaurantId,
                null,
                auth.getName(),
                reason
        );
    }

    private boolean isCardPayment(Payment payment) {
        PaymentMethod method = payment.getMethod();
        return method == PaymentMethod.CARD ||
               method == PaymentMethod.CREDIT_CARD ||
               method == PaymentMethod.DEBIT_CARD ||
               method == PaymentMethod.ONLINE;
    }

    /**
     * Result of refund authorization check
     */
    public record RefundAuthorizationResult(
            boolean allowed,
            boolean needsApproval,
            String reason
    ) {
        public static RefundAuthorizationResult permit() {
            return new RefundAuthorizationResult(true, false, null);
        }

        public static RefundAuthorizationResult denied(String reason) {
            return new RefundAuthorizationResult(false, false, reason);
        }

        public static RefundAuthorizationResult needsApproval(String reason) {
            return new RefundAuthorizationResult(false, true, reason);
        }
    }

    /**
     * Result of void authorization check
     */
    public record VoidAuthorizationResult(
            boolean allowed,
            boolean needsApproval,
            String reason
    ) {
        public static VoidAuthorizationResult permit() {
            return new VoidAuthorizationResult(true, false, null);
        }

        public static VoidAuthorizationResult denied(String reason) {
            return new VoidAuthorizationResult(false, false, reason);
        }

        public static VoidAuthorizationResult needsApproval(String reason) {
            return new VoidAuthorizationResult(false, true, reason);
        }
    }
}
