package com.elcafe.modules.restaurant.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.dto.CreateTableRequest;
import com.elcafe.modules.restaurant.dto.FloorPlanDTO;
import com.elcafe.modules.restaurant.dto.MergeTablesRequest;
import com.elcafe.modules.restaurant.dto.TableResponse;
import com.elcafe.modules.restaurant.dto.UpdateTablePositionRequest;
import com.elcafe.modules.restaurant.dto.UpdateTableRequest;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.mapper.TableMapper;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service("restaurantTableService")
@RequiredArgsConstructor
public class TableService {

    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final TableMapper tableMapper;
    private final OrderRepository orderRepository;

    @Transactional
    public TableResponse createTable(CreateTableRequest request) {
        log.info("Creating table {} for restaurant {}", request.getTableNumber(), request.getRestaurantId());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        // Check if table number already exists for this restaurant
        tableRepository.findByRestaurant_IdAndTableNumber(request.getRestaurantId(), request.getTableNumber())
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
            tableRepository.findByRestaurant_IdAndTableNumber(table.getRestaurant().getId(), request.getTableNumber())
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

        List<RestaurantTable> tables = tableRepository.findByRestaurant_Id(restaurantId);
        return tables.stream()
                .map(tableMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getAvailableTables(Long restaurantId) {
        log.info("Getting available tables for restaurant: {}", restaurantId);

        List<RestaurantTable> tables = tableRepository.findByRestaurant_IdAndStatus(
                restaurantId, RestaurantTable.TableStatus.AVAILABLE);
        return tables.stream()
                .map(tableMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getTablesBySection(Long restaurantId, String section) {
        log.info("Getting tables for restaurant {} in section {}", restaurantId, section);

        List<RestaurantTable> tables = tableRepository.findByRestaurant_IdAndSection(restaurantId, section);
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

    @Transactional
    public List<TableResponse> mergeTables(MergeTablesRequest request) {
        log.info("Merging tables - main: {}, others: {}", request.getMainTableId(), request.getTableIdsToMerge());

        // Validate main table
        RestaurantTable mainTable = tableRepository.findById(request.getMainTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", request.getMainTableId()));

        // Check if main table is already merged
        if (mainTable.getMergedTable() != null) {
            throw new BadRequestException("Main table is already merged with another table");
        }

        // Validate and get all tables to merge
        List<RestaurantTable> tablesToMerge = new ArrayList<>();
        int totalCapacity = mainTable.getCapacity();

        for (Long tableId : request.getTableIdsToMerge()) {
            if (tableId.equals(request.getMainTableId())) {
                throw new BadRequestException("Cannot merge a table with itself");
            }

            RestaurantTable table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new ResourceNotFoundException("Table", "id", tableId));

            // Check if table is already merged
            if (table.getMergedTable() != null) {
                throw new BadRequestException("Table " + table.getTableNumber() + " is already merged");
            }

            // Check if tables are in the same restaurant
            if (!table.getRestaurant().getId().equals(mainTable.getRestaurant().getId())) {
                throw new BadRequestException("All tables must be in the same restaurant");
            }

            tablesToMerge.add(table);
            totalCapacity += table.getCapacity();
        }

        // Save original capacity for main table if not already saved
        if (mainTable.getOriginalCapacity() == null) {
            mainTable.setOriginalCapacity(mainTable.getCapacity());
        }

        // Update main table capacity
        mainTable.setCapacity(totalCapacity);
        tableRepository.save(mainTable);

        // Merge other tables
        for (RestaurantTable table : tablesToMerge) {
            // Save original capacity if not already saved
            if (table.getOriginalCapacity() == null) {
                table.setOriginalCapacity(table.getCapacity());
            }

            table.setMergedTable(mainTable);
            table.setStatus(RestaurantTable.TableStatus.RESERVED); // Mark as reserved since it's part of a merge
            tableRepository.save(table);
        }

        log.info("Successfully merged {} tables with main table {}", tablesToMerge.size(), mainTable.getId());

        // Return all affected tables
        List<TableResponse> responses = new ArrayList<>();
        responses.add(tableMapper.toResponse(mainTable));
        responses.addAll(tablesToMerge.stream()
                .map(tableMapper::toResponse)
                .collect(Collectors.toList()));

        return responses;
    }

    @Transactional
    public List<TableResponse> unmergeTables(Long tableId) {
        log.info("Unmerging tables for table: {}", tableId);

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", tableId));

        List<RestaurantTable> affectedTables = new ArrayList<>();
        RestaurantTable mainTable;

        // Check if this is a main table or a merged table
        if (table.getMergedTable() != null) {
            // This is a merged table, get the main table
            mainTable = table.getMergedTable();
        } else {
            // This might be a main table, check if other tables are merged with it
            mainTable = table;
        }

        // Find all tables merged with the main table
        List<RestaurantTable> mergedTables = tableRepository.findByMergedTable(mainTable);

        if (mergedTables.isEmpty() && mainTable.getMergedTable() == null) {
            throw new BadRequestException("Table is not part of any merge");
        }

        // Restore main table capacity
        if (mainTable.getOriginalCapacity() != null) {
            mainTable.setCapacity(mainTable.getOriginalCapacity());
            mainTable.setOriginalCapacity(null);
        }
        affectedTables.add(mainTable);

        // Unmerge all merged tables
        for (RestaurantTable mergedTable : mergedTables) {
            mergedTable.setMergedTable(null);
            mergedTable.setStatus(RestaurantTable.TableStatus.AVAILABLE);

            // Restore original capacity
            if (mergedTable.getOriginalCapacity() != null) {
                mergedTable.setCapacity(mergedTable.getOriginalCapacity());
                mergedTable.setOriginalCapacity(null);
            }

            tableRepository.save(mergedTable);
            affectedTables.add(mergedTable);
        }

        tableRepository.save(mainTable);

        log.info("Successfully unmerged {} tables", affectedTables.size());

        return affectedTables.stream()
                .map(tableMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getMergedTables(Long tableId) {
        log.info("Getting merged tables for table: {}", tableId);

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", tableId));

        List<RestaurantTable> mergedTables;

        if (table.getMergedTable() != null) {
            // This table is merged with another, get all tables in the merge group
            mergedTables = tableRepository.findByMergedTable(table.getMergedTable());
            mergedTables.add(table.getMergedTable());
        } else {
            // This might be a main table, get tables merged with it
            mergedTables = tableRepository.findByMergedTable(table);
            if (!mergedTables.isEmpty()) {
                mergedTables.add(table);
            }
        }

        return mergedTables.stream()
                .map(tableMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TableResponse updateTablePosition(Long id, UpdateTablePositionRequest request) {
        log.info("Updating position for table {}: x={}, y={}", id, request.getPositionX(), request.getPositionY());

        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table", "id", id));

        table.setPositionX(request.getPositionX());
        table.setPositionY(request.getPositionY());

        if (request.getWidth() != null) {
            table.setWidth(request.getWidth());
        }
        if (request.getHeight() != null) {
            table.setHeight(request.getHeight());
        }

        RestaurantTable updatedTable = tableRepository.save(table);
        log.info("Updated position for table {}", id);

        return tableMapper.toResponse(updatedTable);
    }

    @Transactional(readOnly = true)
    public FloorPlanDTO getFloorPlan(Long restaurantId) {
        log.info("Getting floor plan for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        List<RestaurantTable> tables = tableRepository.findByRestaurant_IdAndActiveTrue(restaurantId);
        List<String> sections = tableRepository.findDistinctSectionsByRestaurantId(restaurantId);

        // Get active order statuses (orders that are still open and can be modified)
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.NEW, OrderStatus.PENDING, OrderStatus.ACCEPTED,
                OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.ON_DELIVERY
        );

        List<FloorPlanDTO.FloorPlanTableDTO> tableDTOs = tables.stream()
                .map(table -> {
                        // Find active order for this table
                        Long currentOrderId = null;
                        if (table.getStatus() == RestaurantTable.TableStatus.OCCUPIED) {
                            List<Order> activeOrders = orderRepository.findByDiningTable_IdAndStatusIn(
                                    table.getId(), activeStatuses);
                            if (!activeOrders.isEmpty()) {
                                currentOrderId = activeOrders.get(0).getId();
                            }
                        }

                        return FloorPlanDTO.FloorPlanTableDTO.builder()
                        .id(table.getId())
                        .tableNumber(table.getTableNumber())
                        .tableName(table.getTableName())
                        .status(table.getStatus())
                        .capacity(table.getCapacity())
                        .section(table.getSection())
                        .positionX(table.getPositionX())
                        .positionY(table.getPositionY())
                        .width(table.getWidth())
                        .height(table.getHeight())
                        // Current order info
                        .currentOrderId(currentOrderId)
                        // Merge info
                        .mergedTable(table.getMergedTable() != null ||
                                tableRepository.findByMergedTable(table).size() > 0)
                        .mergedWithTableId(table.getMergedTable() != null ?
                                table.getMergedTable().getId() : null)
                        .originalCapacity(table.getOriginalCapacity())
                        .build();
                })
                .collect(Collectors.toList());

        return FloorPlanDTO.builder()
                .restaurantId(restaurantId)
                .restaurantName(restaurant.getName())
                .tables(tableDTOs)
                .sections(sections)
                .build();
    }
}
