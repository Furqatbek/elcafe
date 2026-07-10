package com.elcafe.modules.notification.scheduler;

import com.elcafe.modules.notification.service.CustomerNotificationService;
import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import com.elcafe.modules.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduler for sending reservation reminders to customers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    private final CustomerNotificationService customerNotificationService;
    private final ReservationRepository reservationRepository;

    /**
     * Send reminders for today's reservations at 9:00 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    @SchedulerLock(name = "reservation-reminders-today", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void sendDailyReservationReminders() {
        log.info("Sending reservation reminders for today...");

        LocalDate today = LocalDate.now();
        List<Reservation> todaysReservations = reservationRepository
                .findByReservationDateAndStatus(today, ReservationStatus.CONFIRMED);

        int sentCount = 0;
        for (Reservation reservation : todaysReservations) {
            try {
                customerNotificationService.notifyReservationReminder(reservation);
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to send reminder for reservation {}: {}",
                        reservation.getConfirmationCode(), e.getMessage());
            }
        }

        log.info("Sent {} reservation reminders for today", sentCount);
    }

    /**
     * Send reminders for tomorrow's reservations at 6:00 PM
     */
    @Scheduled(cron = "0 0 18 * * *")
    @SchedulerLock(name = "reservation-reminders-tomorrow", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void sendTomorrowReservationReminders() {
        log.info("Sending reservation reminders for tomorrow...");

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Reservation> tomorrowsReservations = reservationRepository
                .findByReservationDateAndStatus(tomorrow, ReservationStatus.CONFIRMED);

        int sentCount = 0;
        for (Reservation reservation : tomorrowsReservations) {
            try {
                customerNotificationService.notifyReservationReminder(reservation);
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to send reminder for reservation {}: {}",
                        reservation.getConfirmationCode(), e.getMessage());
            }
        }

        log.info("Sent {} reservation reminders for tomorrow", sentCount);
    }
}
