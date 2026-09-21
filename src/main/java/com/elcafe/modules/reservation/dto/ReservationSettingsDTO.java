package com.elcafe.modules.reservation.dto;

import com.elcafe.modules.reservation.entity.ReservationSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSettingsDTO {

    private Long id;
    private Long restaurantId;
    private Boolean enabled;
    private Integer advanceDays;
    private Integer minAdvanceHours;
    private Integer slotDurationMinutes;
    private Integer minPartySize;
    private Integer maxPartySize;
    private Boolean depositRequired;
    private BigDecimal depositAmount;
    private BigDecimal depositPercent;
    private Integer cancellationHours;
    private Boolean autoConfirm;
    private Boolean sendReminders;
    private Integer reminderHoursBefore;
    private Integer maxReservationsPerSlot;
    private String notesForCustomers;

    public static ReservationSettingsDTO from(ReservationSettings settings) {
        return ReservationSettingsDTO.builder()
                .id(settings.getId())
                .restaurantId(settings.getRestaurant().getId())
                .enabled(settings.getEnabled())
                .advanceDays(settings.getAdvanceDays())
                .minAdvanceHours(settings.getMinAdvanceHours())
                .slotDurationMinutes(settings.getSlotDurationMinutes())
                .minPartySize(settings.getMinPartySize())
                .maxPartySize(settings.getMaxPartySize())
                .depositRequired(settings.getDepositRequired())
                .depositAmount(settings.getDepositAmount())
                .depositPercent(settings.getDepositPercent())
                .cancellationHours(settings.getCancellationHours())
                .autoConfirm(settings.getAutoConfirm())
                .sendReminders(settings.getSendReminders())
                .reminderHoursBefore(settings.getReminderHoursBefore())
                .maxReservationsPerSlot(settings.getMaxReservationsPerSlot())
                .notesForCustomers(settings.getNotesForCustomers())
                .build();
    }
}
