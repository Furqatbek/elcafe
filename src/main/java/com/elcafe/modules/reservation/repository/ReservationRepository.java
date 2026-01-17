package com.elcafe.modules.reservation.repository;

import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByConfirmationCode(String confirmationCode);

    Page<Reservation> findByRestaurantId(Long restaurantId, Pageable pageable);

    List<Reservation> findByRestaurantIdAndReservationDate(Long restaurantId, LocalDate date);

    List<Reservation> findByRestaurantIdAndReservationDateBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT r FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.reservationDate = :date AND r.status IN :statuses")
    List<Reservation> findByRestaurantIdAndDateAndStatusIn(
            @Param("restaurantId") Long restaurantId,
            @Param("date") LocalDate date,
            @Param("statuses") List<ReservationStatus> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.reservationDate = :date " +
           "AND r.reservationTime BETWEEN :startTime AND :endTime " +
           "AND r.status NOT IN ('CANCELLED', 'NO_SHOW')")
    List<Reservation> findOverlappingReservations(
            @Param("restaurantId") Long restaurantId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.reservationDate = :date " +
           "AND r.reservationTime = :time " +
           "AND r.status NOT IN ('CANCELLED', 'NO_SHOW')")
    long countReservationsAtSlot(
            @Param("restaurantId") Long restaurantId,
            @Param("date") LocalDate date,
            @Param("time") LocalTime time);

    List<Reservation> findByCustomerId(Long customerId);

    @Query("SELECT r FROM Reservation r WHERE r.customerPhone = :phone ORDER BY r.createdAt DESC")
    List<Reservation> findByCustomerPhone(@Param("phone") String phone);

    @Query("SELECT r FROM Reservation r WHERE r.status = :status " +
           "AND r.reservationDate = :date " +
           "AND r.reminderSent = false")
    List<Reservation> findReservationsNeedingReminder(
            @Param("status") ReservationStatus status,
            @Param("date") LocalDate date);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'CONFIRMED' " +
           "AND r.reservationDate < :date")
    List<Reservation> findPastUncompletedReservations(@Param("date") LocalDate date);

    @Query("SELECT r FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.table.id = :tableId " +
           "AND r.reservationDate = :date " +
           "AND r.status NOT IN ('CANCELLED', 'NO_SHOW', 'COMPLETED')")
    List<Reservation> findActiveReservationsForTable(
            @Param("restaurantId") Long restaurantId,
            @Param("tableId") Long tableId,
            @Param("date") LocalDate date);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.status = 'NO_SHOW' " +
           "AND r.customerPhone = :phone")
    long countNoShowsByPhone(@Param("restaurantId") Long restaurantId, @Param("phone") String phone);

    List<Reservation> findByReservationDateAndStatus(LocalDate date, ReservationStatus status);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.reservationDate = :date")
    int countByRestaurantIdAndDate(@Param("restaurantId") Long restaurantId, @Param("date") LocalDate date);
}
