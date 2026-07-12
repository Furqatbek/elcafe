package com.elcafe.modules.restaurant.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.restaurant.dto.CreateWorkingHoursRequest;
import com.elcafe.modules.restaurant.dto.UpdateWorkingHoursRequest;
import com.elcafe.modules.restaurant.dto.WorkingHoursResponse;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.WorkingHours;
import com.elcafe.modules.restaurant.mapper.WorkingHoursMapper;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.WorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingHoursService {

    private final WorkingHoursRepository workingHoursRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final WorkingHoursMapper workingHoursMapper;

    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> getAllByRestaurantId(Long restaurantId) {
        log.debug("Fetching all working hours for restaurant ID: {}", restaurantId);

        // Verify restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        List<WorkingHours> workingHours = workingHoursRepository.findByRestaurant_Id(restaurantId);
        log.debug("Found {} working hours for restaurant ID: {}", workingHours.size(), restaurantId);

        return workingHours.stream()
                .map(workingHoursMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> getAllByUserId(Long userId) {
        log.debug("Fetching all working hours for user ID: {}", userId);

        // Verify user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        List<WorkingHours> workingHours = workingHoursRepository.findByUserId(userId);
        log.debug("Found {} working hours for user ID: {}", workingHours.size(), userId);

        return workingHours.stream()
                .map(workingHoursMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> getAllByRestaurantAndUser(Long restaurantId, Long userId) {
        log.debug("Fetching all working hours for restaurant ID: {} and user ID: {}", restaurantId, userId);

        // Verify restaurant and user exist
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        List<WorkingHours> workingHours = workingHoursRepository.findByRestaurant_IdAndUserId(restaurantId, userId);
        log.debug("Found {} working hours for restaurant ID: {} and user ID: {}", workingHours.size(), restaurantId, userId);

        return workingHours.stream()
                .map(workingHoursMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> getAllByDay(Long restaurantId, DayOfWeek dayOfWeek) {
        log.debug("Fetching all working hours for restaurant ID: {} on {}", restaurantId, dayOfWeek);

        // Verify restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        List<WorkingHours> workingHours = workingHoursRepository.findByRestaurant_IdAndDayOfWeek(restaurantId, dayOfWeek);
        log.debug("Found {} working hours for restaurant ID: {} on {}", workingHours.size(), restaurantId, dayOfWeek);

        return workingHours.stream()
                .map(workingHoursMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WorkingHoursResponse getById(Long id) {
        log.debug("Fetching working hours with ID: {}", id);

        WorkingHours workingHours = workingHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkingHours", "id", id));

        return workingHoursMapper.toResponse(workingHours);
    }

    @Transactional
    public WorkingHoursResponse create(CreateWorkingHoursRequest request) {
        log.info("Creating new working hours for user ID: {} at restaurant ID: {}", request.getUserId(), request.getRestaurantId());

        // Verify restaurant exists
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        // Verify user exists
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        // Validate time range
        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().equals(request.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }

        WorkingHours workingHours = workingHoursMapper.toEntity(request);
        workingHours.setRestaurant(restaurant);
        workingHours.setUser(user);

        WorkingHours savedWorkingHours = workingHoursRepository.save(workingHours);
        log.info("Working hours created with ID: {}", savedWorkingHours.getId());

        return workingHoursMapper.toResponse(savedWorkingHours);
    }

    @Transactional
    public WorkingHoursResponse update(Long id, UpdateWorkingHoursRequest request) {
        log.info("Updating working hours with ID: {}", id);

        WorkingHours workingHours = workingHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkingHours", "id", id));

        // Validate time range
        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().equals(request.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }

        workingHoursMapper.updateEntity(workingHours, request);

        WorkingHours updatedWorkingHours = workingHoursRepository.save(workingHours);
        log.info("Working hours updated successfully: {}", id);

        return workingHoursMapper.toResponse(updatedWorkingHours);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting working hours with ID: {}", id);

        WorkingHours workingHours = workingHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkingHours", "id", id));

        workingHoursRepository.delete(workingHours);
        log.info("Working hours deleted successfully: {}", id);
    }

    @Transactional
    public void deleteAllByRestaurantId(Long restaurantId) {
        log.info("Deleting all working hours for restaurant ID: {}", restaurantId);

        // Verify restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        workingHoursRepository.deleteByRestaurantId(restaurantId);
        log.info("All working hours deleted for restaurant ID: {}", restaurantId);
    }

    @Transactional
    public void deleteAllByUserId(Long userId) {
        log.info("Deleting all working hours for user ID: {}", userId);

        // Verify user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        workingHoursRepository.deleteByUserId(userId);
        log.info("All working hours deleted for user ID: {}", userId);
    }
}
