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

    /**
     * Find reservations within a shift time range (supports midnight-crossing shifts).
     * This query combines reservationDate and reservationTime to filter by datetime range.
     *
     * For shifts that cross midnight (e.g., 10 AM - 2 AM), this query will correctly include
     * reservations that fall in the early morning hours of the next calendar day.
     */
    @Query("SELECT r FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND ((r.reservationDate > :startDate) " +
           "  OR (r.reservationDate = :startDate AND r.reservationTime >= :startTime)) " +
           "AND ((r.reservationDate < :endDate) " +
           "  OR (r.reservationDate = :endDate AND r.reservationTime < :endTime))")
    List<Reservation> findByRestaurantIdAndShiftTimeRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime);

    /**
     * Count reservations within a shift time range (supports midnight-crossing shifts).
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND ((r.reservationDate > :startDate) " +
           "  OR (r.reservationDate = :startDate AND r.reservationTime >= :startTime)) " +
           "AND ((r.reservationDate < :endDate) " +
           "  OR (r.reservationDate = :endDate AND r.reservationTime < :endTime))")
    int countByRestaurantIdAndShiftTimeRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime);

    @Query("SELECT r FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.reservationDate = :date AND r.status IN :statuses")
    List<Reservation> findByRestaurantIdAndDateAndStatusIn(
            @Param("restaurantId") Long restaurantId,
            @Param("date") LocalDate date,
            @Param("statuses") List<ReservationStatus> statuses);

    /**
     * Shift-aware version: Find reservations within a shift time range with specific statuses.
     */
    @Query("SELECT r FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.status IN :statuses " +
           "AND ((r.reservationDate > :startDate) " +
           "  OR (r.reservationDate = :startDate AND r.reservationTime >= :startTime)) " +
           "AND ((r.reservationDate < :endDate) " +
           "  OR (r.reservationDate = :endDate AND r.reservationTime < :endTime))")
    List<Reservation> findByRestaurantIdAndShiftTimeRangeAndStatusIn(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime,
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

    /**
     * Shift-aware version: Find active reservations for a specific table within a shift time range.
     */
    @Query("SELECT r FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.table.id = :tableId " +
           "AND r.status NOT IN ('CANCELLED', 'NO_SHOW', 'COMPLETED') " +
           "AND ((r.reservationDate > :startDate) " +
           "  OR (r.reservationDate = :startDate AND r.reservationTime >= :startTime)) " +
           "AND ((r.reservationDate < :endDate) " +
           "  OR (r.reservationDate = :endDate AND r.reservationTime < :endTime))")
    List<Reservation> findActiveReservationsForTableInShiftTimeRange(
            @Param("restaurantId") Long restaurantId,
            @Param("tableId") Long tableId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.status = 'NO_SHOW' " +
           "AND r.customerPhone = :phone")
    long countNoShowsByPhone(@Param("restaurantId") Long restaurantId, @Param("phone") String phone);

    List<Reservation> findByReservationDateAndStatus(LocalDate date, ReservationStatus status);

    /**
     * Shift-aware version: Find reservations within a shift time range with a specific status.
     * Useful for finding all reservations in a shift's time range filtered by status.
     */
    @Query("SELECT r FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.status = :status " +
           "AND ((r.reservationDate > :startDate) " +
           "  OR (r.reservationDate = :startDate AND r.reservationTime >= :startTime)) " +
           "AND ((r.reservationDate < :endDate) " +
           "  OR (r.reservationDate = :endDate AND r.reservationTime < :endTime))")
    List<Reservation> findByRestaurantIdAndShiftTimeRangeAndStatus(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime,
            @Param("status") ReservationStatus status);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.restaurant.id = :restaurantId " +
           "AND r.reservationDate = :date")
    int countByRestaurantIdAndDate(@Param("restaurantId") Long restaurantId, @Param("date") LocalDate date);
}
