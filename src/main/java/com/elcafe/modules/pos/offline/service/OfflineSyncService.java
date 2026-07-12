package com.elcafe.modules.pos.offline.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.service.POSOrderService;
import com.elcafe.modules.pos.offline.dto.*;
import com.elcafe.modules.pos.offline.entity.OfflineOrder;
import com.elcafe.modules.pos.offline.entity.POSDevice;
import com.elcafe.modules.pos.offline.enums.OfflineSyncStatus;
import com.elcafe.modules.pos.offline.repository.OfflineOrderRepository;
import com.elcafe.modules.pos.offline.repository.POSDeviceRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for handling offline order synchronization.
 * Manages the queue of offline orders and syncs them when connectivity is restored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineSyncService {

    private static final int MAX_SYNC_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MINUTES = 5;

    private final OfflineOrderRepository offlineOrderRepository;
    private final POSDeviceRepository posDeviceRepository;
    private final RestaurantRepository restaurantRepository;
    private final POSOrderService posOrderService;
    private final ObjectMapper objectMapper;

    /**
     * Register a POS device for offline mode.
     */
    @Transactional
    public POSDevice registerDevice(Long restaurantId, DeviceRegistrationRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        // Check if device already exists
        Optional<POSDevice> existing = posDeviceRepository.findByRestaurantIdAndDeviceId(
            restaurantId, request.getDeviceId());

        if (existing.isPresent()) {
            POSDevice device = existing.get();
            device.setDeviceName(request.getDeviceName());
            device.setDeviceType(request.getDeviceType());
            device.setOfflineEnabled(true);
            device.setIsActive(true);
            device.recordHeartbeat();
            return posDeviceRepository.save(device);
        }

        POSDevice device = POSDevice.builder()
            .restaurant(restaurant)
            .deviceId(request.getDeviceId())
            .deviceName(request.getDeviceName())
            .deviceType(request.getDeviceType())
            .offlineEnabled(true)
            .isActive(true)
            .build();
        device.recordHeartbeat();

        return posDeviceRepository.save(device);
    }

    /**
     * Record device heartbeat for connectivity monitoring.
     */
    @Transactional
    public void recordHeartbeat(Long restaurantId, String deviceId) {
        posDeviceRepository.findByRestaurantIdAndDeviceId(restaurantId, deviceId)
            .ifPresent(device -> {
                device.recordHeartbeat();
                posDeviceRepository.save(device);
            });
    }

    /**
     * Queue an offline order for later synchronization.
     */
    @Transactional
    public OfflineOrder queueOfflineOrder(Long restaurantId, OfflineOrderRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        // Check for duplicate
        Optional<OfflineOrder> existing = offlineOrderRepository
            .findByRestaurantIdAndDeviceIdAndClientOrderId(
                restaurantId, request.getDeviceId(), request.getClientOrderId());

        if (existing.isPresent()) {
            log.info("Duplicate offline order detected: {}", request.getClientOrderId());
            return existing.get();
        }

        OfflineOrder offlineOrder = OfflineOrder.builder()
            .restaurant(restaurant)
            .deviceId(request.getDeviceId())
            .clientOrderId(request.getClientOrderId())
            .orderData(request.getOrderData())
            .syncStatus(OfflineSyncStatus.PENDING)
            .build();

        return offlineOrderRepository.save(offlineOrder);
    }

    /**
     * Batch queue multiple offline orders.
     */
    @Transactional
    public List<OfflineOrder> queueOfflineOrders(Long restaurantId, List<OfflineOrderRequest> requests) {
        return requests.stream()
            .map(request -> queueOfflineOrder(restaurantId, request))
            .collect(Collectors.toList());
    }

    /**
     * Sync a single offline order.
     */
    @Transactional
    public SyncResult syncOfflineOrder(OfflineOrder offlineOrder) {
        try {
            offlineOrder.setSyncStatus(OfflineSyncStatus.SYNCING);
            offlineOrder.incrementSyncAttempts();
            offlineOrderRepository.save(offlineOrder);

            // Convert order data to create order request
            Map<String, Object> orderData = offlineOrder.getOrderData();

            // Call the POS order service to create the order
            Order order = posOrderService.createOrderFromOffline(
                offlineOrder.getRestaurant().getId(),
                orderData,
                offlineOrder.getClientOrderId(),
                offlineOrder.getDeviceId()
            );

            offlineOrder.markSynced(order);
            offlineOrderRepository.save(offlineOrder);

            log.info("Successfully synced offline order: {} -> Order ID: {}",
                offlineOrder.getClientOrderId(), order.getId());

            return SyncResult.success(offlineOrder.getClientOrderId(), order.getId());

        } catch (Exception e) {
            log.error("Failed to sync offline order: {}", offlineOrder.getClientOrderId(), e);

            if (offlineOrder.getSyncAttempts() >= MAX_SYNC_ATTEMPTS) {
                offlineOrder.markFailed(e.getMessage());
            } else {
                offlineOrder.setSyncStatus(OfflineSyncStatus.PENDING);
                offlineOrder.setSyncError(e.getMessage());
            }
            offlineOrderRepository.save(offlineOrder);

            return SyncResult.failure(offlineOrder.getClientOrderId(), e.getMessage());
        }
    }

    /**
     * Sync all pending offline orders for a device.
     */
    @Transactional
    public BatchSyncResult syncDeviceOrders(Long restaurantId, String deviceId) {
        List<OfflineOrder> pendingOrders = offlineOrderRepository
            .findByRestaurantIdAndDeviceIdAndSyncStatus(restaurantId, deviceId, OfflineSyncStatus.PENDING);

        List<SyncResult> results = new ArrayList<>();
        for (OfflineOrder order : pendingOrders) {
            results.add(syncOfflineOrder(order));
        }

        // Update device sync timestamp
        posDeviceRepository.findByRestaurantIdAndDeviceId(restaurantId, deviceId)
            .ifPresent(device -> {
                device.recordSync();
                posDeviceRepository.save(device);
            });

        return BatchSyncResult.of(results);
    }

    /**
     * Scheduled task to retry failed synchronizations.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @SchedulerLock(name = "offline-sync-retry", lockAtLeastFor = "PT30S")
    @Transactional
    public void retryFailedSyncs() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);
        List<OfflineOrder> failedOrders = offlineOrderRepository.findFailedOrdersForRetry(cutoff);

        for (OfflineOrder order : failedOrders) {
            if (order.getSyncAttempts() < MAX_SYNC_ATTEMPTS) {
                order.setSyncStatus(OfflineSyncStatus.PENDING);
                offlineOrderRepository.save(order);
            }
        }

        // Process pending orders
        List<OfflineOrder> pendingOrders = offlineOrderRepository
            .findPendingForSync(OfflineSyncStatus.PENDING, MAX_SYNC_ATTEMPTS);

        for (OfflineOrder order : pendingOrders) {
            syncOfflineOrder(order);
        }
    }

    /**
     * Get sync status for a restaurant.
     */
    public OfflineSyncStatus getSyncStatus(Long restaurantId) {
        long pending = offlineOrderRepository.countByRestaurantIdAndStatus(
            restaurantId, OfflineSyncStatus.PENDING);
        long failed = offlineOrderRepository.countByRestaurantIdAndStatus(
            restaurantId, OfflineSyncStatus.FAILED);

        if (failed > 0) return OfflineSyncStatus.FAILED;
        if (pending > 0) return OfflineSyncStatus.PENDING;
        return OfflineSyncStatus.SYNCED;
    }

    /**
     * Get list of online/offline devices for a restaurant.
     */
    public DeviceStatusResponse getDeviceStatus(Long restaurantId) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(2);

        List<POSDevice> onlineDevices = posDeviceRepository.findOnlineDevices(restaurantId, cutoff);
        List<POSDevice> offlineDevices = posDeviceRepository.findOfflineDevices(restaurantId, cutoff);
        long pendingOrders = offlineOrderRepository.countByRestaurantIdAndStatus(
            restaurantId, OfflineSyncStatus.PENDING);

        return DeviceStatusResponse.builder()
            .onlineDevices(onlineDevices.stream().map(this::toDeviceInfo).collect(Collectors.toList()))
            .offlineDevices(offlineDevices.stream().map(this::toDeviceInfo).collect(Collectors.toList()))
            .pendingOrdersCount(pendingOrders)
            .build();
    }

    private DeviceInfo toDeviceInfo(POSDevice device) {
        return DeviceInfo.builder()
            .id(device.getId())
            .deviceId(device.getDeviceId())
            .deviceName(device.getDeviceName())
            .deviceType(device.getDeviceType())
            .lastHeartbeat(device.getLastHeartbeat())
            .lastSync(device.getLastSync())
            .isOnline(device.isOnline())
            .build();
    }
}
