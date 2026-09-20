package com.elcafe.exception;

import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Thrown when a clock-in is refused because the subject already has an ACTIVE
 * shift.
 *
 * Carries the existing shift so the caller can recover instead of being stuck.
 * The waiter mobile app keeps its shift in memory for the life of the process —
 * there is no page reload — so a waiter who works overnight can return the next
 * day still holding a stale shift and be unable to clock in. Returning the real
 * active shift in the error lets the client resync itself rather than relying on
 * the user to log out.
 */
@Getter
public class ActiveShiftExistsException extends RuntimeException {

    private final Long activeShiftId;
    private final LocalDate shiftDate;
    private final OffsetDateTime clockIn;

    public ActiveShiftExistsException(String message,
                                      Long activeShiftId,
                                      LocalDate shiftDate,
                                      OffsetDateTime clockIn) {
        super(message);
        this.activeShiftId = activeShiftId;
        this.shiftDate = shiftDate;
        this.clockIn = clockIn;
    }
}
