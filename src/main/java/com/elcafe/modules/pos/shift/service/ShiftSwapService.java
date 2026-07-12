package com.elcafe.modules.pos.shift.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.pos.shift.entity.ShiftSchedule;
import com.elcafe.modules.pos.shift.entity.ShiftSwapRequest;
import com.elcafe.modules.pos.shift.repository.ShiftScheduleRepository;
import com.elcafe.modules.pos.shift.repository.ShiftSwapRequestRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftSwapService {

    private final ShiftSwapRequestRepository swapRepository;
    private final ShiftScheduleRepository scheduleRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    @Transactional
    public ShiftSwapRequest createRequest(Long restaurantId, Long requestingEmployeeId,
                                           Long targetEmployeeId, Long scheduleId,
                                           LocalDate shiftDate, String reason) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        User requesting = userRepository.findById(requestingEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        User target = targetEmployeeId != null
                ? userRepository.findById(targetEmployeeId).orElse(null) : null;
        ShiftSchedule schedule = scheduleId != null
                ? scheduleRepository.findById(scheduleId).orElse(null) : null;

        ShiftSwapRequest request = ShiftSwapRequest.builder()
                .restaurant(restaurant)
                .requestingEmployee(requesting)
                .targetEmployee(target)
                .schedule(schedule)
                .shiftDate(shiftDate)
                .reason(reason)
                .status(ShiftSwapRequest.Status.PENDING)
                .build();

        request = swapRepository.save(request);
        log.info("Shift swap request created: {} by {} for {}",
                request.getId(), requesting.getFullName(), shiftDate);
        return request;
    }

    @Transactional
    public ShiftSwapRequest acceptRequest(Long requestId) {
        ShiftSwapRequest request = swapRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Swap request not found"));

        if (request.getStatus() != ShiftSwapRequest.Status.PENDING) {
            throw new BadRequestException("Request is not pending");
        }

        request.setStatus(ShiftSwapRequest.Status.ACCEPTED);
        log.info("Shift swap accepted: {}", requestId);
        return swapRepository.save(request);
    }

    @Transactional
    public ShiftSwapRequest rejectRequest(Long requestId) {
        ShiftSwapRequest request = swapRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Swap request not found"));

        request.setStatus(ShiftSwapRequest.Status.REJECTED);
        log.info("Shift swap rejected: {}", requestId);
        return swapRepository.save(request);
    }

    @Transactional
    public ShiftSwapRequest approveRequest(Long requestId, Long managerId) {
        ShiftSwapRequest request = swapRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Swap request not found"));

        if (request.getStatus() != ShiftSwapRequest.Status.ACCEPTED) {
            throw new BadRequestException("Request must be accepted before approval");
        }

        User manager = userRepository.findById(managerId).orElse(null);
        request.setApprovedBy(manager);
        request.setStatus(ShiftSwapRequest.Status.APPROVED);

        // Swap the schedule: reassign to target employee
        if (request.getSchedule() != null && request.getTargetEmployee() != null) {
            ShiftSchedule schedule = request.getSchedule();
            schedule.setEmployee(request.getTargetEmployee());
            scheduleRepository.save(schedule);
            log.info("Schedule {} reassigned from {} to {}",
                    schedule.getId(),
                    request.getRequestingEmployee().getFullName(),
                    request.getTargetEmployee().getFullName());
        }

        log.info("Shift swap approved: {} by manager {}", requestId, managerId);
        return swapRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<ShiftSwapRequest> getPendingRequests(Long restaurantId) {
        return swapRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(
                restaurantId, ShiftSwapRequest.Status.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ShiftSwapRequest> getAllRequests(Long restaurantId) {
        return swapRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }
}
