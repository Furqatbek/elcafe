package com.elcafe.modules.waiter.enums;

/**
 * Status of a restaurant table
 */
public enum TableStatus {
    FREE,           // Available for customers
    ORDERING,       // Waiter is taking order
    WAITING,        // Order submitted, waiting for kitchen
    SERVED,         // Food served, customers eating
    BILL_REQUESTED, // Customers requested bill
    OCCUPIED,       // General occupied status
    RESERVED,       // Reserved for future use
    CLEANING        // Being cleaned after customers left
}
