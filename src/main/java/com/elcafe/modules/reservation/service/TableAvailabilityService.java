package com.elcafe.modules.reservation.service;

import com.elcafe.modules.reservation.dto.TableAvailabilityResponse;
import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import com.elcafe.modules.reservation.repository.ReservationRepository;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableAvailabilityService {

    private final RestaurantTableRepository tableRepository;
    private final ReservationRepository reservationRepository;

    private static final Set<ReservationStatus> BLOCKING_STATUSES = Set.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.DEPOSIT_PENDING,
            ReservationStatus.SEATED
    );

    /**
     * Get all tables with their availability for a specific date/time
     */
    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> getTablesWithAvailability(
            Long restaurantId, LocalDate date, LocalTime time, int partySize) {

        // Get all active tables
        List<RestaurantTable> tables = tableRepository.findByRestaurant_IdAndActiveTrue(restaurantId);

        // Get reservations for this date that could conflict
        List<Reservation> reservations = reservationRepository
                .findByRestaurantIdAndReservationDate(restaurantId, date)
                .stream()
                .filter(r -> BLOCKING_STATUSES.contains(r.getStatus()))
                .filter(r -> isTimeOverlapping(r.getReservationTime(), r.getDurationMinutes(), time))
                .toList();

        // Build table availability map
        Set<Long> reservedTableIds = reservations.stream()
                .filter(r -> r.getTable() != null)
                .map(r -> r.getTable().getId())
                .collect(Collectors.toSet());

        return tables.stream()
                .map(table -> {
                    boolean isAvailable = !reservedTableIds.contains(table.getId())
                            && table.getCapacity() >= partySize
                            && table.getStatus() != RestaurantTable.TableStatus.OUT_OF_SERVICE;

                    String status = determineTableStatus(table, reservedTableIds, partySize);

                    return TableAvailabilityResponse.builder()
                            .id(table.getId())
                            .tableNumber(table.getTableNumber())
                            .tableName(table.getTableName())
                            .capacity(table.getCapacity())
                            .section(table.getSection())
                            .positionX(table.getPositionX())
                            .positionY(table.getPositionY())
                            .width(table.getWidth())
                            .height(table.getHeight())
                            .currentStatus(table.getStatus().name())
                            .availableForReservation(isAvailable)
                            .statusReason(status)
                            .build();
                })
                .sorted((a, b) -> {
                    // Sort by section, then by table number
                    int sectionCompare = compareNullable(a.getSection(), b.getSection());
                    if (sectionCompare != 0) return sectionCompare;
                    return compareNullable(a.getTableNumber(), b.getTableNumber());
                })
                .toList();
    }

    /**
     * Get distinct table sections for a restaurant
     */
    @Transactional(readOnly = true)
    public List<String> getTableSections(Long restaurantId) {
        return tableRepository.findDistinctSectionsByRestaurantId(restaurantId);
    }

    /**
     * Check if a specific table is available at a given date/time
     */
    @Transactional(readOnly = true)
    public boolean isTableAvailable(Long tableId, LocalDate date, LocalTime time, int duration) {
        RestaurantTable table = tableRepository.findById(tableId).orElse(null);
        if (table == null || !table.getActive() ||
                table.getStatus() == RestaurantTable.TableStatus.OUT_OF_SERVICE) {
            return false;
        }

        // Check for conflicting reservations
        List<Reservation> conflictingReservations = reservationRepository
                .findByRestaurantIdAndReservationDate(table.getRestaurant().getId(), date)
                .stream()
                .filter(r -> r.getTable() != null && r.getTable().getId().equals(tableId))
                .filter(r -> BLOCKING_STATUSES.contains(r.getStatus()))
                .filter(r -> isTimeOverlapping(r.getReservationTime(), r.getDurationMinutes(), time))
                .toList();

        return conflictingReservations.isEmpty();
    }

    private boolean isTimeOverlapping(LocalTime existingTime, int existingDuration, LocalTime requestedTime) {
        LocalTime existingEnd = existingTime.plusMinutes(existingDuration);
        LocalTime requestedEnd = requestedTime.plusMinutes(60); // Assume 60 min default

        // Check if times overlap
        return !requestedTime.isAfter(existingEnd) && !requestedEnd.isBefore(existingTime);
    }

    private String determineTableStatus(RestaurantTable table, Set<Long> reservedTableIds, int partySize) {
        if (table.getStatus() == RestaurantTable.TableStatus.OUT_OF_SERVICE) {
            return "OUT_OF_SERVICE";
        }
        if (reservedTableIds.contains(table.getId())) {
            return "RESERVED";
        }
        if (table.getCapacity() < partySize) {
            return "TOO_SMALL";
        }
        if (table.getStatus() == RestaurantTable.TableStatus.OCCUPIED) {
            return "OCCUPIED";
        }
        if (table.getStatus() == RestaurantTable.TableStatus.CLEANING) {
            return "CLEANING";
        }
        return "AVAILABLE";
    }

    private int compareNullable(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }
}
