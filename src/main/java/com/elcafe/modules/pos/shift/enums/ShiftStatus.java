package com.elcafe.modules.pos.shift.enums;

/**
 * Status of an employee shift.
 */
public enum ShiftStatus {
    ACTIVE,     // Employee currently working
    ON_BREAK,   // Employee on break
    COMPLETED,  // Shift ended, pending approval
    APPROVED,   // Manager approved
    DISPUTED    // Variance or issue flagged
}
