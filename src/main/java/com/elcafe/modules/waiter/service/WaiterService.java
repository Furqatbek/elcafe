package com.elcafe.modules.waiter.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.waiter.dto.CreateWaiterRequest;
import com.elcafe.modules.waiter.dto.UpdateWaiterRequest;
import com.elcafe.modules.waiter.dto.WaiterAuthRequest;
import com.elcafe.modules.waiter.dto.WaiterAuthResponse;
import com.elcafe.modules.waiter.dto.WaiterResponse;
import com.elcafe.modules.waiter.entity.Table;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterTable;
import com.elcafe.modules.waiter.repository.TableRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.modules.waiter.repository.WaiterTableRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing waiters
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaiterService {

    private final WaiterRepository waiterRepository;
    private final WaiterTableRepository waiterTableRepository;
    private final TableRepository tableRepository;
    private final ObjectMapper objectMapper;

    /**
     * Get all waiters with pagination
     */
    @Transactional(readOnly = true)
    public Page<WaiterResponse> getAllWaiters(Pageable pageable) {
        return waiterRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    /**
     * Get waiter by ID
     */
    @Transactional(readOnly = true)
    public WaiterResponse getById(Long id) {
        Waiter waiter = waiterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + id));
        return convertToResponse(waiter);
    }

    /**
     * Get all active waiters
     */
    @Transactional(readOnly = true)
    public List<WaiterResponse> getActiveWaiters() {
        return waiterRepository.findByActiveTrue().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Create new waiter
     */
    @Transactional
    public WaiterResponse createWaiter(CreateWaiterRequest request) {
        // Validate PIN code uniqueness
        if (waiterRepository.existsByPinCode(request.getPinCode())) {
            throw new BadRequestException("PIN code already exists");
        }

        // Validate email uniqueness if provided
        if (request.getEmail() != null && waiterRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        // Convert permissions list to JSON
        String permissionsJson = null;
        if (request.getPermissions() != null && !request.getPermissions().isEmpty()) {
            try {
                permissionsJson = objectMapper.writeValueAsString(request.getPermissions());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize permissions", e);
            }
        }

        Waiter waiter = Waiter.builder()
                .name(request.getName())
                .pinCode(request.getPinCode())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .role(request.getRole())
                .active(request.getActive() != null ? request.getActive() : true)
                .permissions(permissionsJson)
                .build();

        Waiter saved = waiterRepository.save(waiter);
        log.info("Created waiter: {} with role: {}", saved.getName(), saved.getRole());

        return convertToResponse(saved);
    }

    /**
     * Update waiter
     */
    @Transactional
    public WaiterResponse updateWaiter(Long id, UpdateWaiterRequest request) {
        Waiter waiter = waiterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + id));

        if (request.getName() != null) {
            waiter.setName(request.getName());
        }

        if (request.getPinCode() != null && !request.getPinCode().equals(waiter.getPinCode())) {
            if (waiterRepository.existsByPinCode(request.getPinCode())) {
                throw new BadRequestException("PIN code already exists");
            }
            waiter.setPinCode(request.getPinCode());
        }

        if (request.getEmail() != null && !request.getEmail().equals(waiter.getEmail())) {
            if (waiterRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email already exists");
            }
            waiter.setEmail(request.getEmail());
        }

        if (request.getPhoneNumber() != null) {
            waiter.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getRole() != null) {
            waiter.setRole(request.getRole());
        }

        if (request.getActive() != null) {
            waiter.setActive(request.getActive());
        }

        if (request.getPermissions() != null) {
            try {
                String permissionsJson = objectMapper.writeValueAsString(request.getPermissions());
                waiter.setPermissions(permissionsJson);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize permissions", e);
            }
        }

        Waiter updated = waiterRepository.save(waiter);
        log.info("Updated waiter: {}", updated.getName());

        return convertToResponse(updated);
    }

    /**
     * Delete waiter
     */
    @Transactional
    public void deleteWaiter(Long id) {
        Waiter waiter = waiterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + id));

        // Unassign from all tables first
        List<WaiterTable> assignments = waiterTableRepository.findByWaiterIdAndActiveTrue(id);
        assignments.forEach(WaiterTable::unassign);
        waiterTableRepository.saveAll(assignments);

        waiterRepository.delete(waiter);
        log.info("Deleted waiter: {}", waiter.getName());
    }

    /**
     * Authenticate waiter with PIN code
     */
    @Transactional(readOnly = true)
    public WaiterAuthResponse authenticate(WaiterAuthRequest request) {
        Waiter waiter = waiterRepository.findByPinCode(request.getPinCode())
                .orElseThrow(() -> new BadRequestException("Invalid PIN code"));

        if (!waiter.getActive()) {
            throw new BadRequestException("Waiter account is inactive");
        }

        // In a real implementation, you would generate a JWT token here
        String token = "waiter_token_" + waiter.getId(); // Placeholder

        return WaiterAuthResponse.builder()
                .waiterId(waiter.getId())
                .name(waiter.getName())
                .token(token)
                .waiter(convertToResponse(waiter))
                .build();
    }

    /**
     * Assign waiter to a table
     */
    @Transactional
    public void assignToTable(Long waiterId, Long tableId) {
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        // Check if table already has an active assignment
        waiterTableRepository.findByTableIdAndActiveTrue(tableId)
                .ifPresent(wt -> {
                    throw new BadRequestException("Table is already assigned to another waiter");
                });

        // Create new assignment
        WaiterTable assignment = WaiterTable.builder()
                .waiter(waiter)
                .table(table)
                .active(true)
                .build();

        waiterTableRepository.save(assignment);

        // Update table's current waiter
        table.setCurrentWaiter(waiter);
        tableRepository.save(table);

        log.info("Assigned waiter {} to table {}", waiter.getName(), table.getNumber());
    }

    /**
     * Unassign waiter from a table
     */
    @Transactional
    public void unassignFromTable(Long waiterId, Long tableId) {
        WaiterTable assignment = waiterTableRepository.findByWaiterIdAndTableIdAndActiveTrue(waiterId, tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Active assignment not found"));

        assignment.unassign();
        waiterTableRepository.save(assignment);

        // Clear table's current waiter
        Table table = assignment.getTable();
        table.setCurrentWaiter(null);
        tableRepository.save(table);

        log.info("Unassigned waiter from table {}", table.getNumber());
    }

    /**
     * Get active tables for a waiter
     */
    @Transactional(readOnly = true)
    public List<Table> getActiveTables(Long waiterId) {
        return tableRepository.findByWaiterId(waiterId);
    }

    /**
     * Convert Waiter to response DTO
     */
    private WaiterResponse convertToResponse(Waiter waiter) {
        List<String> permissions = new ArrayList<>();
        if (waiter.getPermissions() != null && !waiter.getPermissions().isEmpty()) {
            try {
                permissions = objectMapper.readValue(waiter.getPermissions(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize permissions", e);
            }
        }

        long activeTablesCount = waiterTableRepository.countByWaiterIdAndActiveTrue(waiter.getId());

        return WaiterResponse.builder()
                .id(waiter.getId())
                .name(waiter.getName())
                .email(waiter.getEmail())
                .phoneNumber(waiter.getPhoneNumber())
                .role(waiter.getRole())
                .active(waiter.getActive())
                .permissions(permissions)
                .activeTablesCount((int) activeTablesCount)
                .createdAt(waiter.getCreatedAt())
                .updatedAt(waiter.getUpdatedAt())
                .build();
    }
}
