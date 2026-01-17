package com.elcafe.modules.reservation.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.reservation.dto.CreateReservationRequest;
import com.elcafe.modules.reservation.dto.ReservationResponse;
import com.elcafe.modules.reservation.entity.Reservation;
import com.elcafe.modules.reservation.entity.ReservationSettings;
import com.elcafe.modules.reservation.enums.ReservationSource;
import com.elcafe.modules.reservation.enums.ReservationStatus;
import com.elcafe.modules.reservation.repository.ReservationRepository;
import com.elcafe.modules.reservation.repository.ReservationSettingsRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.notification.service.CustomerNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSettingsRepository settingsRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final RestaurantTableRepository tableRepository;
    private final AvailabilityService availabilityService;
    @Lazy private final OwnerNotificationService ownerNotificationService;
    @Lazy private final CustomerNotificationService customerNotificationService;

    private static final String CONFIRMATION_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom random = new SecureRandom();

    /**
     * Create a new reservation
     */
    @Transactional
    public ReservationResponse createReservation(Long restaurantId, CreateReservationRequest request) {
        log.info("Creating reservation for restaurant {} on {} at {}",
                restaurantId, request.getReservationDate(), request.getReservationTime());

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        ReservationSettings settings = settingsRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> createDefaultSettings(restaurant));

        // Validate the reservation
        validateReservation(settings, request);

        // Check availability
        if (!availabilityService.isSlotAvailable(restaurantId, request.getReservationDate(),
                request.getReservationTime(), request.getPartySize())) {
            throw new BadRequestException("The selected time slot is not available");
        }

        // Find or create customer
        Customer customer = findOrCreateCustomer(restaurantId, request);

        // Find table if specified
        RestaurantTable table = null;
        if (request.getTableId() != null) {
            table = tableRepository.findById(request.getTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("Table", "id", request.getTableId()));
        }

        // Determine duration
        int duration = request.getDurationMinutes() != null ?
                request.getDurationMinutes() : settings.getSlotDurationMinutes();

        // Build reservation
        Reservation reservation = Reservation.builder()
                .restaurant(restaurant)
                .customer(customer)
                .table(table)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .reservationDate(request.getReservationDate())
                .reservationTime(request.getReservationTime())
                .partySize(request.getPartySize())
                .durationMinutes(duration)
                .specialRequests(request.getSpecialRequests())
                .occasion(request.getOccasion())
                .confirmationCode(generateConfirmationCode())
                .depositRequired(settings.getDepositRequired())
                .depositAmount(settings.getDepositAmount())
                .source(parseSource(request.getSource()))
                .build();

        // Set initial status
        if (settings.getDepositRequired() && settings.getDepositAmount() != null &&
                settings.getDepositAmount().doubleValue() > 0) {
            reservation.setStatus(ReservationStatus.DEPOSIT_PENDING);
        } else if (settings.getAutoConfirm()) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservation.setConfirmedAt(LocalDateTime.now());
        } else {
            reservation.setStatus(ReservationStatus.PENDING);
        }

        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation created with ID: {} and code: {}", saved.getId(), saved.getConfirmationCode());

        // Notify owners/staff via Telegram
        try {
            if (ownerNotificationService != null) {
                ownerNotificationService.notifyNewReservation(saved);
            }
        } catch (Exception e) {
            log.error("Failed to send owner notification for reservation, but reservation was created successfully", e);
        }

        return ReservationResponse.from(saved);
    }

    /**
     * Get reservation by ID
     */
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", id));
        return ReservationResponse.from(reservation);
    }

    /**
     * Get reservation by confirmation code
     */
    @Transactional(readOnly = true)
    public ReservationResponse getReservationByCode(String confirmationCode) {
        Reservation reservation = reservationRepository.findByConfirmationCode(confirmationCode.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "confirmationCode", confirmationCode));
        return ReservationResponse.from(reservation);
    }

    /**
     * Get all reservations for a restaurant
     */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getReservations(Long restaurantId, Pageable pageable) {
        return reservationRepository.findByRestaurantId(restaurantId, pageable)
                .map(ReservationResponse::from);
    }

    /**
     * Get reservations for a specific date
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByDate(Long restaurantId, LocalDate date) {
        return reservationRepository.findByRestaurantIdAndReservationDate(restaurantId, date)
                .stream()
                .map(ReservationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get reservations for a date range (for calendar view)
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByDateRange(
            Long restaurantId, LocalDate startDate, LocalDate endDate) {
        return reservationRepository.findByRestaurantIdAndReservationDateBetween(restaurantId, startDate, endDate)
                .stream()
                .map(ReservationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Confirm a reservation
     */
    @Transactional
    public ReservationResponse confirmReservation(Long id, Long userId) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", id));

        if (reservation.getStatus() != ReservationStatus.PENDING &&
                reservation.getStatus() != ReservationStatus.DEPOSIT_PENDING) {
            throw new BadRequestException("Reservation cannot be confirmed in current status");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setConfirmedAt(LocalDateTime.now());
        // Note: confirmedBy would need User lookup if userId provided

        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation {} confirmed", id);

        // Notify customer via Telegram
        try {
            if (customerNotificationService != null) {
                customerNotificationService.notifyReservationConfirmed(saved);
            }
        } catch (Exception e) {
            log.error("Failed to send customer confirmation for reservation {}: {}", id, e.getMessage());
        }

        return ReservationResponse.from(saved);
    }

    /**
     * Cancel a reservation
     */
    @Transactional
    public ReservationResponse cancelReservation(Long id, String reason) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", id));

        if (!reservation.canBeCancelled()) {
            throw new BadRequestException("Reservation cannot be cancelled in current status");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setCancellationReason(reason);

        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation {} cancelled", id);

        // Notify owners/staff about cancellation
        try {
            if (ownerNotificationService != null) {
                ownerNotificationService.notifyReservationCancelled(saved, reason);
            }
        } catch (Exception e) {
            log.error("Failed to send owner cancellation notification for reservation {}: {}", id, e.getMessage());
        }

        // Notify customer via Telegram
        try {
            if (customerNotificationService != null) {
                customerNotificationService.notifyReservationCancelled(saved, reason);
            }
        } catch (Exception e) {
            log.error("Failed to send customer cancellation notification for reservation {}: {}", id, e.getMessage());
        }

        return ReservationResponse.from(saved);
    }

    /**
     * Check in a guest (mark as seated)
     */
    @Transactional
    public ReservationResponse checkIn(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", id));

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BadRequestException("Only confirmed reservations can be checked in");
        }

        reservation.setStatus(ReservationStatus.SEATED);
        reservation.setCheckedInAt(LocalDateTime.now());

        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation {} checked in", id);

        return ReservationResponse.from(saved);
    }

    /**
     * Complete a reservation
     */
    @Transactional
    public ReservationResponse completeReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", id));

        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setCompletedAt(LocalDateTime.now());

        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation {} completed", id);

        return ReservationResponse.from(saved);
    }

    /**
     * Mark as no-show
     */
    @Transactional
    public ReservationResponse markNoShow(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", id));

        reservation.setStatus(ReservationStatus.NO_SHOW);
        reservation.setNoShow(true);

        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation {} marked as no-show", id);

        return ReservationResponse.from(saved);
    }

    /**
     * Assign a table to reservation
     */
    @Transactional
    public ReservationResponse assignTable(Long reservationId, Long tableId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", reservationId));

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", tableId));

        reservation.setTable(table);
        Reservation saved = reservationRepository.save(reservation);

        log.info("Table {} assigned to reservation {}", tableId, reservationId);
        return ReservationResponse.from(saved);
    }

    /**
     * Get customer's reservations by phone
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByPhone(String phone) {
        return reservationRepository.findByCustomerPhone(phone)
                .stream()
                .map(ReservationResponse::from)
                .collect(Collectors.toList());
    }

    // Helper methods

    private void validateReservation(ReservationSettings settings, CreateReservationRequest request) {
        // Check if reservations are enabled
        if (!settings.getEnabled()) {
            throw new BadRequestException("Online reservations are not available for this restaurant");
        }

        // Check party size
        if (request.getPartySize() < settings.getMinPartySize()) {
            throw new BadRequestException("Party size must be at least " + settings.getMinPartySize());
        }
        if (request.getPartySize() > settings.getMaxPartySize()) {
            throw new BadRequestException("Party size cannot exceed " + settings.getMaxPartySize());
        }

        // Check advance booking
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(settings.getAdvanceDays());

        if (request.getReservationDate().isAfter(maxDate)) {
            throw new BadRequestException("Cannot book more than " + settings.getAdvanceDays() + " days in advance");
        }

        // Check minimum advance time
        if (request.getReservationDate().equals(today)) {
            LocalTime minTime = LocalTime.now().plusHours(settings.getMinAdvanceHours());
            if (request.getReservationTime().isBefore(minTime)) {
                throw new BadRequestException("Reservations must be made at least " +
                        settings.getMinAdvanceHours() + " hours in advance");
            }
        }
    }

    private String generateConfirmationCode() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(CONFIRMATION_CHARS.charAt(random.nextInt(CONFIRMATION_CHARS.length())));
        }
        return code.toString();
    }

    private ReservationSource parseSource(String source) {
        if (source == null || source.isBlank()) {
            return ReservationSource.WEBSITE;
        }
        try {
            return ReservationSource.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ReservationSource.WEBSITE;
        }
    }

    private ReservationSettings createDefaultSettings(Restaurant restaurant) {
        ReservationSettings settings = ReservationSettings.builder()
                .restaurant(restaurant)
                .enabled(true)
                .build();
        return settingsRepository.save(settings);
    }

    /**
     * Find existing customer by phone or create a new one
     */
    private Customer findOrCreateCustomer(Long restaurantId, CreateReservationRequest request) {
        // If customerId is provided, use that
        if (request.getCustomerId() != null) {
            return customerRepository.findById(request.getCustomerId()).orElse(null);
        }

        // If phone is provided, try to find existing customer
        if (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank()) {
            String normalizedPhone = normalizePhone(request.getCustomerPhone());

            // Try to find existing customer by phone
            Customer existingCustomer = customerRepository.findByPhone(normalizedPhone).orElse(null);

            if (existingCustomer != null) {
                log.info("Found existing customer by phone: {}", existingCustomer.getId());
                // Update name if provided and different
                if (request.getCustomerName() != null && !request.getCustomerName().isBlank()) {
                    String[] names = request.getCustomerName().trim().split("\\s+", 2);
                    if (!names[0].equals(existingCustomer.getFirstName())) {
                        existingCustomer.setFirstName(names[0]);
                        existingCustomer.setLastName(names.length > 1 ? names[1] : "");
                        existingCustomer = customerRepository.save(existingCustomer);
                    }
                }
                return existingCustomer;
            }

            // Create new customer
            log.info("Creating new customer for phone: {}", normalizedPhone);
            String[] names = (request.getCustomerName() != null && !request.getCustomerName().isBlank())
                    ? request.getCustomerName().trim().split("\\s+", 2)
                    : new String[]{"Guest", ""};

            Customer newCustomer = Customer.builder()
                    .firstName(names[0])
                    .lastName(names.length > 1 ? names[1] : "")
                    .phone(normalizedPhone)
                    .registrationSource(com.elcafe.modules.customer.enums.RegistrationSource.RESERVATION)
                    .active(true)
                    .build();

            return customerRepository.save(newCustomer);
        }

        return null;
    }

    /**
     * Normalize phone number (remove spaces, dashes, etc.)
     */
    private String normalizePhone(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("[\\s\\-()]+", "").trim();
    }
}
