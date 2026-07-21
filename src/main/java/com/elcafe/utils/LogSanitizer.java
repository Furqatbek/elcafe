package com.elcafe.utils;

/**
 * Redacts PII before it reaches application logs. Logs must not carry raw phone numbers, names, or
 * dates of birth (production go-live gate A6 — PII must not land in retained/shipped logs).
 * {@link #phone(String)} keeps only the last four digits so support can still correlate a record
 * without the full identifier being logged.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /**
     * Mask a phone number for logging: keep the last 4 characters, redact the rest. Null/blank-safe.
     * e.g. {@code +998901234567 -> ***4567}.
     */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "<none>";
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "***" + trimmed.substring(trimmed.length() - 4);
    }
}
