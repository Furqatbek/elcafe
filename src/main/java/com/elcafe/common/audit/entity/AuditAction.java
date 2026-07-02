package com.elcafe.common.audit.entity;

/**
 * Enumeration of all auditable actions in the system.
 * Security-critical operations are tracked for compliance and fraud prevention.
 */
public enum AuditAction {

    // ==================== ORDER OPERATIONS ====================
    ORDER_CREATED("Order created"),
    ORDER_MODIFIED("Order modified"),
    ORDER_CANCELLED("Order cancelled"),
    ORDER_VOIDED("Order voided"),
    ORDER_VOID_REQUESTED("Order void requested (pending approval)"),
    ORDER_VOID_APPROVED("Order void approved by manager"),
    ORDER_VOID_REJECTED("Order void rejected by manager"),
    ORDER_STATUS_CHANGED("Order status changed"),
    ORDER_DELETED("Order soft deleted"),
    ORDER_RESTORED("Order restored from deletion"),

    // ==================== PAYMENT OPERATIONS ====================
    PAYMENT_INITIATED("Payment initiated"),
    PAYMENT_COMPLETED("Payment completed"),
    PAYMENT_FAILED("Payment failed"),
    PAYMENT_CANCELLED("Payment cancelled"),

    // ==================== REFUND OPERATIONS ====================
    REFUND_INITIATED("Refund initiated"),
    REFUND_COMPLETED("Refund completed"),
    REFUND_FAILED("Refund failed"),
    REFUND_DENIED("Refund denied - insufficient permissions"),
    REFUND_REQUESTED("Refund requested (pending approval)"),
    REFUND_APPROVED("Refund approved by manager"),
    REFUND_REJECTED("Refund rejected by manager"),

    // ==================== VOID OPERATIONS ====================
    VOID_INITIATED("Void operation initiated"),
    VOID_COMPLETED("Void operation completed"),
    VOID_DENIED("Void denied - policy violation"),

    // ==================== DISCOUNT/PROMOTION OPERATIONS ====================
    DISCOUNT_APPLIED("Discount applied"),
    DISCOUNT_REMOVED("Discount removed"),
    MANUAL_DISCOUNT_APPLIED("Manual discount applied"),
    PROMOTION_APPLIED("Promotion applied"),
    COUPON_REDEEMED("Coupon redeemed"),

    // ==================== CASH OPERATIONS ====================
    CASH_DRAWER_OPENED("Cash drawer opened"),
    CASH_DRAWER_RECONCILED("Cash drawer reconciled"),
    CASH_DISCREPANCY_REPORTED("Cash discrepancy reported"),
    CASH_DROP_PERFORMED("Cash drop performed"),

    // ==================== PRICE MODIFICATIONS ====================
    PRICE_OVERRIDE("Price override applied"),
    ITEM_COMPED("Item comped (free)"),

    // ==================== ACCESS/AUTHENTICATION ====================
    LOGIN_SUCCESS("User logged in"),
    LOGIN_FAILED("Login attempt failed"),
    LOGOUT("User logged out"),
    SESSION_TIMEOUT("Session timed out"),
    PASSWORD_CHANGED("Password changed"),
    PERMISSION_DENIED("Permission denied"),

    // ==================== STAFF OPERATIONS ====================
    STAFF_CREATED("Staff account created"),
    STAFF_MODIFIED("Staff account modified"),
    STAFF_DEACTIVATED("Staff account deactivated"),
    ROLE_CHANGED("User role changed"),
    PERMISSIONS_MODIFIED("User permissions modified"),

    // ==================== INVENTORY ====================
    STOCK_ADJUSTED("Stock level adjusted"),
    STOCK_WRITE_OFF("Stock written off"),
    WASTE_RECORDED("Waste recorded"),

    // ==================== REPORTING ====================
    REPORT_GENERATED("Report generated"),
    DATA_EXPORTED("Data exported"),

    // ==================== SETTINGS ====================
    SETTINGS_MODIFIED("System settings modified"),
    TAX_RATE_CHANGED("Tax rate changed"),
    PRICING_CHANGED("Menu pricing changed"),

    // ==================== SUBSCRIPTION / BILLING ====================
    PLAN_CHANGED("Restaurant subscription plan changed"),
    RESTAURANT_SUSPENDED("Restaurant suspended by platform operator"),
    RESTAURANT_REACTIVATED("Restaurant reactivated by platform operator"),
    SUBSCRIPTION_CANCELLED("Subscription cancelled by platform operator");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
