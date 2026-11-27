package com.elcafe.exception;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Pulls the database constraint name out of a failed write.
 *
 * Note that {@code getMostSpecificCause()} is the wrong tool for this: it
 * returns the innermost cause, which is the driver's own SQLException, while
 * the constraint name is carried by Hibernate's ConstraintViolationException
 * one level above it. So walk the chain instead.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** The violated constraint's name, or null if the chain does not name one. */
    public static String nameOf(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return null;
    }
}
