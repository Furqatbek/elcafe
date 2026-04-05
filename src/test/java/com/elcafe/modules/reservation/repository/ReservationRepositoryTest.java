package com.elcafe.modules.reservation.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class ReservationRepositoryTest {

    @Autowired private ReservationRepository reservationRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private int seq = 0;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);
    }

    private Reservation createReservation(LocalDate date, LocalTime time,
                                           ReservationStatus status, String phone) {
        Reservation r = Reservation.builder()
                .restaurant(restaurant)
                .customerName("Guest " + (++seq))
                .customerPhone(phone)
                .reservationDate(date)
                .reservationTime(time)
                .partySize(4)
                .durationMinutes(60)
                .status(status)
                .confirmationCode("CONF-" + System.nanoTime() + "-" + seq)
                .reminderSent(false)
                .depositRequired(false)
                .depositPaid(false)
                .build();
        em.persist(r);
        return r;
    }

    @Test
    @DisplayName("findOverlappingReservations excludes CANCELLED and NO_SHOW")
    void findOverlappingReservations() {
        LocalDate today = LocalDate.now();
        LocalTime noon = LocalTime.of(12, 0);

        createReservation(today, noon, ReservationStatus.CONFIRMED, "111");
        createReservation(today, noon.plusMinutes(30), ReservationStatus.PENDING, "222");
        createReservation(today, noon, ReservationStatus.CANCELLED, "333");
        createReservation(today, noon, ReservationStatus.NO_SHOW, "444");
        // Outside time window
        createReservation(today, LocalTime.of(18, 0), ReservationStatus.CONFIRMED, "555");

        em.flush();
        em.clear();

        List<Reservation> overlapping = reservationRepository.findOverlappingReservations(
                restaurant.getId(), today, noon, noon.plusHours(1));

        assertEquals(2, overlapping.size());
        assertTrue(overlapping.stream().noneMatch(r ->
                r.getStatus() == ReservationStatus.CANCELLED || r.getStatus() == ReservationStatus.NO_SHOW));
    }

    @Test
    @DisplayName("countReservationsAtSlot counts active reservations at exact time slot")
    void countReservationsAtSlot() {
        LocalDate today = LocalDate.now();
        LocalTime noon = LocalTime.of(12, 0);

        createReservation(today, noon, ReservationStatus.CONFIRMED, "111");
        createReservation(today, noon, ReservationStatus.PENDING, "222");
        createReservation(today, noon, ReservationStatus.CANCELLED, "333");
        createReservation(today, noon.plusHours(1), ReservationStatus.CONFIRMED, "444");

        em.flush();
        em.clear();

        long count = reservationRepository.countReservationsAtSlot(
                restaurant.getId(), today, noon);

        assertEquals(2L, count);
    }

    @Test
    @DisplayName("findReservationsNeedingReminder returns unreminded reservations with specific status and date")
    void findReservationsNeedingReminder() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        createReservation(tomorrow, LocalTime.of(12, 0), ReservationStatus.CONFIRMED, "111");
        createReservation(tomorrow, LocalTime.of(14, 0), ReservationStatus.CONFIRMED, "222");

        // Already reminded
        Reservation reminded = createReservation(tomorrow, LocalTime.of(16, 0),
                ReservationStatus.CONFIRMED, "333");
        reminded.setReminderSent(true);

        // Wrong status
        createReservation(tomorrow, LocalTime.of(18, 0), ReservationStatus.CANCELLED, "444");

        // Wrong date
        createReservation(LocalDate.now().plusDays(3), LocalTime.of(12, 0),
                ReservationStatus.CONFIRMED, "555");

        em.flush();
        em.clear();

        List<Reservation> needReminder = reservationRepository.findReservationsNeedingReminder(
                ReservationStatus.CONFIRMED, tomorrow);

        assertEquals(2, needReminder.size());
        assertTrue(needReminder.stream().allMatch(r -> !r.getReminderSent()));
    }
}
