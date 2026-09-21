package com.elcafe.modules.sms.enums;

public enum TargetAudience {
    ALL,                // All customers
    SEGMENT,            // Specific customer segment
    CUSTOM,             // Custom list of phone numbers
    BIRTHDAY_TODAY,     // Customers with birthday today
    INACTIVE,           // Inactive customers (configurable days)
    NEW_CUSTOMERS,      // Customers registered in last X days
    LOYAL_CUSTOMERS,    // Customers with X+ orders
    HIGH_VALUE          // Customers with total spend above threshold
}
