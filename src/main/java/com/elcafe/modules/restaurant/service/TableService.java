package com.elcafe.modules.restaurant.service;

import com.elcafe.exceptions.ResourceNotFoundException;
import com.elcafe.modules.restaurant.dto.CreateTableRequest;
import com.elcafe.modules.restaurant.dto.TableResponse;
import com.elcafe.modules.restaurant.dto.UpdateTableRequest;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.mapper.TableMapper;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final TableMapper tableMapper;

    @Transactional
    public TableResponse createTable(CreateTableRequest request) {
        log.info("Creating table {} for restaurant {}", request.getTableNumber(), request.getRestaurantId());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        // Check if table number already exists for this restaurant
        tableRepository.findByRestaurantIdAndTableNumber(request.getRestaurantId(), request.getTableNumber())
                .ifPresent(t -> {
                    throw new IllegalArgumentException("Table number " + request.getTableNumber() + " already exists");
                });

        RestaurantTable table = tableMapper.toEntity(request);
        table.setRestaurant(restaurant);

        RestaurantTable savedTable = tableRepository.save(table);
        log.info("Created table with ID: {}", savedTable.getId());

        return tableMapper.toResponse(savedTable);
    }

    @Transactional
    public TableResponse updateTable(Long id, UpdateTableRequest request) {
        log.info("Updating table ID: {}", id);

        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", id));

        // If updating table number, check uniqueness
        if (request.getTableNumber() != null && !request.getTableNumber().equals(table.getTableNumber())) {
            tableRepository.findByRestaurantIdAndTableNumber(table.getRestaurant().getId(), request.getTableNumber())
                    .ifPresent(t -> {
                        throw new IllegalArgumentException("Table number " + request.getTableNumber() + " already exists");
                    });
        }

        tableMapper.updateEntity(table, request);
        RestaurantTable updatedTable = tableRepository.save(table);

        return tableMapper.toResponse(updatedTable);
    }

    @Transactional(readOnly = true)
    public TableResponse getTableById(Long id) {
        log.info("Getting table by ID: {}", id);

        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", id));

        return tableMapper.toResponse(table);
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getTablesByRestaurant(Long restaurantId) {
        log.info("Getting tables for restaurant: {}", restaurantId);

        List<RestaurantTable> tables = tableRepository.findByRestaurantId(restaurantId);
        return tables.stream()
                .map(tableMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getAvailableTables(Long restaurantId) {
        log.info("Getting available tables for restaurant: {}", restaurantId);

        List<RestaurantTable> tables = tableRepository.findByRestaurantIdAndStatus(
                restaurantId, RestaurantTable.TableStatus.AVAILABLE);
        return tables.stream()
                .map(tableMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getTablesBySection(Long restaurantId, String section) {
        log.info("Getting tables for restaurant {} in section {}", restaurantId, section);

        List<RestaurantTable> tables = tableRepository.findByRestaurantIdAndSection(restaurantId, section);
        return tables.stream()
                .map(tableMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getSections(Long restaurantId) {
        log.info("Getting sections for restaurant: {}", restaurantId);
        return tableRepository.findDistinctSectionsByRestaurantId(restaurantId);
    }

    @Transactional
    public TableResponse updateTableStatus(Long id, RestaurantTable.TableStatus status) {
        log.info("Updating table {} status to {}", id, status);

        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", id));

        table.setStatus(status);
        RestaurantTable updatedTable = tableRepository.save(table);

        return tableMapper.toResponse(updatedTable);
    }

    @Transactional
    public void deleteTable(Long id) {
        log.info("Deleting table: {}", id);

        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", id));

        tableRepository.delete(table);
        log.info("Deleted table: {}", id);
    }

    @Transactional(readOnly = true)
    public Long countTablesByStatus(Long restaurantId, RestaurantTable.TableStatus status) {
        return tableRepository.countByRestaurantIdAndStatus(restaurantId, status);
    }
}
