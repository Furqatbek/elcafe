package com.elcafe.modules.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Who registered how many guests at the till, and whether those guests turned out to be real.
 *
 * <p>This is the report the welcome-bonus design promised. The staff-assisted door skips OTP so the
 * queue keeps moving — the employee standing with the guest is the trust anchor instead — and the whole
 * trade rests on that path being <em>reviewable afterwards</em>. {@code registered_by_user_id} has been
 * written since V182; until this report existed nothing ever read it, which made the guard theoretical.
 *
 * <p>Two numbers, not one. <b>Registrations</b> answers "who is using this door". <b>Reached</b> answers
 * the question that actually matters: an unverified number may simply be wrong, and a guest whose phone
 * has a mistyped digit still counts as a registration, still collects a bonus, and can never be
 * contacted again. A cashier with forty registrations and four reachable phones is a different story
 * from one with forty and thirty-eight, and only the second number tells them apart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffRegistrationReport {

    private LocalDate from;
    /** Inclusive — the report covers whole days in the restaurant's own reckoning. */
    private LocalDate to;

    private int totalRegistrations;
    private int totalReached;
    private BigDecimal totalBonusGranted;

    /** Busiest first: the point of the report is that an outlier is visible at a glance. */
    private List<StaffRow> staff;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffRow {
        private Long userId;
        /** Falls back to the id when the employee record is gone — a deleted user must not hide a row. */
        private String staffName;
        private String role;

        private int registrations;

        /**
         * How many of those guests an SMS has actually been delivered to. Deliberately not "how many
         * have a phone number" — every one of them does; the question is whether it was the right one.
         */
        private int reached;

        /** {@code reached / registrations}, 0–100, rounded. Null when there are no registrations. */
        private Integer reachRatePercent;

        /** What this employee's registrations cost in welcome bonus, read from the actual grants. */
        private BigDecimal bonusGranted;

        /** Per day, so "forty guests on a quiet Tuesday" is visible rather than averaged away. */
        private List<DailyCount> daily;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCount {
        private LocalDate date;
        private int registrations;
    }
}
