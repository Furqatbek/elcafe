package com.elcafe.modules.reservation.dto;

import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.reservation.enums.ReservationSource;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private Long customerId;
    private Long tableId;
    private String tableName;

    private String customerName;
    private String customerPhone;
    private String customerEmail;

    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private LocalTime endTime;
    private Integer partySize;
    private Integer durationMinutes;

    private ReservationStatus status;
    private String specialRequests;
    private String occasion;

    private String confirmationCode;
    private LocalDateTime confirmedAt;

    private LocalDateTime checkedInAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private Boolean noShow;

    private Boolean depositRequired;
    private BigDecimal depositAmount;
    private Boolean depositPaid;

    private ReservationSource source;
    private Boolean reminderSent;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .restaurantId(reservation.getRestaurant().getId())
                .restaurantName(reservation.getRestaurant().getName())
                .customerId(reservation.getCustomer() != null ? reservation.getCustomer().getId() : null)
                .tableId(reservation.getTable() != null ? reservation.getTable().getId() : null)
                .tableName(reservation.getTable() != null ? reservation.getTable().getName() : null)
                .customerName(reservation.getCustomerName())
                .customerPhone(reservation.getCustomerPhone())
                .customerEmail(reservation.getCustomerEmail())
                .reservationDate(reservation.getReservationDate())
                .reservationTime(reservation.getReservationTime())
                .endTime(reservation.getEndTime())
                .partySize(reservation.getPartySize())
                .durationMinutes(reservation.getDurationMinutes())
                .status(reservation.getStatus())
                .specialRequests(reservation.getSpecialRequests())
                .occasion(reservation.getOccasion())
                .confirmationCode(reservation.getConfirmationCode())
                .confirmedAt(reservation.getConfirmedAt())
                .checkedInAt(reservation.getCheckedInAt())
                .completedAt(reservation.getCompletedAt())
                .cancelledAt(reservation.getCancelledAt())
                .cancellationReason(reservation.getCancellationReason())
                .noShow(reservation.getNoShow())
                .depositRequired(reservation.getDepositRequired())
                .depositAmount(reservation.getDepositAmount())
                .depositPaid(reservation.getDepositPaid())
                .source(reservation.getSource())
                .reminderSent(reservation.getReminderSent())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }
}
