package com.elcafe.modules.waiter.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.dto.CreateTableRequest;
import com.elcafe.modules.waiter.dto.TableResponse;
import com.elcafe.modules.waiter.dto.UpdateTableRequest;
import com.elcafe.modules.waiter.entity.Table;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterTable;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.elcafe.modules.waiter.enums.TableStatus;
import com.elcafe.modules.waiter.repository.TableRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.modules.waiter.repository.WaiterTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing restaurant tables
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TableService {

    private final TableRepository tableRepository;
    private final WaiterRepository waiterRepository;
    private final WaiterTableRepository waiterTableRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderEventService orderEventService;

    /**
     * Get all tables with pagination
     */
    @Transactional(readOnly = true)
    public Page<TableResponse> getAllTables(Pageable pageable) {
        return tableRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    /**
     * Get table by ID
     */
    @Transactional(readOnly = true)
    public TableResponse getById(Long id) {
        Table table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));
        return convertToResponse(table);
    }

    /**
     * Get tables by status
     */
    @Transactional(readOnly = true)
    public List<TableResponse> getByStatus(TableStatus status) {
        return tableRepository.findByStatus(status).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get available tables
     */
    @Transactional(readOnly = true)
    public List<TableResponse> getAvailableTables() {
        return tableRepository.findAvailableTables().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Create new table
     */
    @Transactional
    public TableResponse createTable(CreateTableRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found with id: " + request.getRestaurantId()));

        // Check if table number already exists for this restaurant
        if (tableRepository.existsByRestaurantIdAndNumber(request.getRestaurantId(), request.getNumber())) {
            throw new BadRequestException("Table number already exists for this restaurant");
        }

        Table table = Table.builder()
                .restaurant(restaurant)
                .number(request.getNumber())
                .capacity(request.getCapacity())
                .floor(request.getFloor())
                .section(request.getSection())
                .status(TableStatus.FREE)
                .build();

        Table saved = tableRepository.save(table);
        log.info("Created table {} for restaurant {}", saved.getNumber(), restaurant.getName());

        return convertToResponse(saved);
    }

    /**
     * Update table
     */
    @Transactional
    public TableResponse updateTable(Long id, UpdateTableRequest request) {
        Table table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        if (request.getNumber() != null && !request.getNumber().equals(table.getNumber())) {
            if (tableRepository.existsByRestaurantIdAndNumber(table.getRestaurant().getId(), request.getNumber())) {
                throw new BadRequestException("Table number already exists for this restaurant");
            }
            table.setNumber(request.getNumber());
        }

        if (request.getCapacity() != null) {
            table.setCapacity(request.getCapacity());
        }

        if (request.getFloor() != null) {
            table.setFloor(request.getFloor());
        }

        if (request.getSection() != null) {
            table.setSection(request.getSection());
        }

        if (request.getStatus() != null) {
            table.setStatus(request.getStatus());
        }

        Table updated = tableRepository.save(table);
        log.info("Updated table {}", updated.getNumber());

        return convertToResponse(updated);
    }

    /**
     * Delete table
     */
    @Transactional
    public void deleteTable(Long id) {
        Table table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));

        // Check if table has active orders
        long activeOrders = table.getOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .count();

        if (activeOrders > 0) {
            throw new BadRequestException("Cannot delete table with active orders");
        }

        tableRepository.delete(table);
        log.info("Deleted table {}", table.getNumber());
    }

    /**
     * Open a table (mark as occupied)
     */
    @Transactional
    public TableResponse openTable(Long tableId, Long waiterId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        if (!table.isAvailable()) {
            throw new BadRequestException("Table is not available");
        }

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        table.setStatus(TableStatus.OCCUPIED);
        table.setCurrentWaiter(waiter);
        table.setOpenedAt(LocalDateTime.now());
        table.setClosedAt(null);

        Table updated = tableRepository.save(table);
        log.info("Opened table {} by waiter {}", table.getNumber(), waiter.getName());

        return convertToResponse(updated);
    }

    /**
     * Close a table (mark as free)
     */
    @Transactional
    public TableResponse closeTable(Long tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        // Check if table has active orders
        long activeOrders = table.getOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .count();

        if (activeOrders > 0) {
            throw new BadRequestException("Cannot close table with active orders");
        }

        table.setStatus(TableStatus.CLEANING);
        table.setClosedAt(LocalDateTime.now());

        Table updated = tableRepository.save(table);
        log.info("Closed table {}", table.getNumber());

        return convertToResponse(updated);
    }

    /**
     * Merge two tables
     */
    @Transactional
    public TableResponse mergeTables(Long sourceTableId, Long targetTableId, String waiterName) {
        Table sourceTable = tableRepository.findById(sourceTableId)
                .orElseThrow(() -> new ResourceNotFoundException("Source table not found with id: " + sourceTableId));

        Table targetTable = tableRepository.findById(targetTableId)
                .orElseThrow(() -> new ResourceNotFoundException("Target table not found with id: " + targetTableId));

        if (sourceTable.isMerged() || targetTable.isMerged()) {
            throw new BadRequestException("One or both tables are already merged");
        }

        // Merge source table into target
        sourceTable.setMergedWith(targetTable);
        sourceTable.setStatus(TableStatus.OCCUPIED);

        tableRepository.save(sourceTable);

        // Move all orders from source to target
        sourceTable.getOrders().forEach(order -> {
            order.setTable(targetTable);

            // Record merge event for each order
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceTableId", sourceTableId);
            metadata.put("targetTableId", targetTableId);
            metadata.put("sourceTableNumber", sourceTable.getNumber());
            metadata.put("targetTableNumber", targetTable.getNumber());

            orderEventService.publishEvent(order, OrderEventType.TABLE_MERGED, waiterName, metadata);
        });

        log.info("Merged table {} into table {}", sourceTable.getNumber(), targetTable.getNumber());

        return convertToResponse(targetTable);
    }

    /**
     * Unmerge tables
     */
    @Transactional
    public TableResponse unmergeTables(Long tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        if (!table.isMerged()) {
            throw new BadRequestException("Table is not merged");
        }

        table.setMergedWith(null);
        table.setStatus(TableStatus.FREE);

        Table updated = tableRepository.save(table);
        log.info("Unmerged table {}", table.getNumber());

        return convertToResponse(updated);
    }

    /**
     * Assign waiter to table
     */
    @Transactional
    public TableResponse assignWaiter(Long tableId, Long waiterId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));

        // Unassign current waiter if exists
        if (table.getCurrentWaiter() != null) {
            waiterTableRepository.findByWaiterIdAndTableIdAndActiveTrue(
                    table.getCurrentWaiter().getId(), tableId)
                    .ifPresent(wt -> {
                        wt.unassign();
                        waiterTableRepository.save(wt);
                    });
        }

        // Create new assignment
        WaiterTable assignment = WaiterTable.builder()
                .waiter(waiter)
                .table(table)
                .active(true)
                .build();

        waiterTableRepository.save(assignment);

        table.setCurrentWaiter(waiter);
        Table updated = tableRepository.save(table);

        log.info("Assigned waiter {} to table {}", waiter.getName(), table.getNumber());

        // Record event for any active orders
        table.getOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .forEach(order -> orderEventService.recordEvent(order, OrderEventType.WAITER_ASSIGNED, waiter.getName()));

        return convertToResponse(updated);
    }

    /**
     * Unassign waiter from table
     */
    @Transactional
    public TableResponse unassignWaiter(Long tableId) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        if (table.getCurrentWaiter() == null) {
            throw new BadRequestException("Table has no assigned waiter");
        }

        String waiterName = table.getCurrentWaiter().getName();

        // Deactivate assignment
        waiterTableRepository.findByTableIdAndActiveTrue(tableId)
                .ifPresent(wt -> {
                    wt.unassign();
                    waiterTableRepository.save(wt);
                });

        table.setCurrentWaiter(null);
        Table updated = tableRepository.save(table);

        log.info("Unassigned waiter from table {}", table.getNumber());

        // Record event for any active orders
        table.getOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .forEach(order -> orderEventService.recordEvent(order, OrderEventType.WAITER_UNASSIGNED, waiterName));

        return convertToResponse(updated);
    }

    /**
     * Update table status
     */
    @Transactional
    public TableResponse updateStatus(Long tableId, TableStatus status) {
        Table table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        table.setStatus(status);
        Table updated = tableRepository.save(table);

        log.info("Updated table {} status to {}", table.getNumber(), status);

        return convertToResponse(updated);
    }

    /**
     * Convert Table to response DTO
     */
    private TableResponse convertToResponse(Table table) {
        long activeOrders = table.getOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .count();

        return TableResponse.builder()
                .id(table.getId())
                .restaurantId(table.getRestaurant().getId())
                .number(table.getNumber())
                .capacity(table.getCapacity())
                .floor(table.getFloor())
                .section(table.getSection())
                .status(table.getStatus())
                .currentWaiterId(table.getCurrentWaiter() != null ? table.getCurrentWaiter().getId() : null)
                .currentWaiterName(table.getCurrentWaiter() != null ? table.getCurrentWaiter().getName() : null)
                .mergedWithId(table.getMergedWith() != null ? table.getMergedWith().getId() : null)
                .mergedWithNumber(table.getMergedWith() != null ? table.getMergedWith().getNumber() : null)
                .isMerged(table.isMerged())
                .isAvailable(table.isAvailable())
                .activeOrdersCount((int) activeOrders)
                .openedAt(table.getOpenedAt())
                .closedAt(table.getClosedAt())
                .createdAt(table.getCreatedAt())
                .updatedAt(table.getUpdatedAt())
                .build();
    }
}
