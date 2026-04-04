package com.elcafe.modules.pos.offline.service;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfflineSyncServiceTest {

    @Mock private OfflineOrderRepository offlineOrderRepository;
    @Mock private POSDeviceRepository posDeviceRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private POSOrderService posOrderService;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private OfflineSyncService offlineSyncService;

    private Restaurant restaurant;
    private POSDevice device;
    private OfflineOrder offlineOrder;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        device = POSDevice.builder()
                .id(1L).restaurant(restaurant)
                .deviceId("POS-001").deviceName("Register 1")
                .offlineEnabled(true).isActive(true).build();

        offlineOrder = OfflineOrder.builder()
                .id(1L).restaurant(restaurant).deviceId("POS-001")
                .clientOrderId("CLT-001")
                .orderData(Map.of("items", List.of()))
                .syncStatus(OfflineSyncStatus.PENDING)
                .syncAttempts(0).build();
    }

    @Test @DisplayName("registerDevice — creates device record")
    void registerDevice_success() {
        DeviceRegistrationRequest request = new DeviceRegistrationRequest();
        request.setDeviceId("POS-002");
        request.setDeviceName("Register 2");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(posDeviceRepository.findByRestaurantIdAndDeviceId(1L, "POS-002")).thenReturn(Optional.empty());
        when(posDeviceRepository.save(any())).thenAnswer(i -> { POSDevice d = i.getArgument(0); d.setId(2L); return d; });

        POSDevice result = offlineSyncService.registerDevice(1L, request);

        assertThat(result.getDeviceId()).isEqualTo("POS-002");
        assertThat(result.getIsActive()).isTrue();
    }

    @Test @DisplayName("registerDevice — updates existing device")
    void registerDevice_updatesExisting() {
        DeviceRegistrationRequest request = new DeviceRegistrationRequest();
        request.setDeviceId("POS-001");
        request.setDeviceName("Updated Name");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(posDeviceRepository.findByRestaurantIdAndDeviceId(1L, "POS-001")).thenReturn(Optional.of(device));
        when(posDeviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        POSDevice result = offlineSyncService.registerDevice(1L, request);

        assertThat(result.getDeviceName()).isEqualTo("Updated Name");
    }

    @Test @DisplayName("queueOfflineOrder — queues for sync")
    void queueOfflineOrder_success() {
        OfflineOrderRequest request = new OfflineOrderRequest();
        request.setDeviceId("POS-001");
        request.setClientOrderId("CLT-002");
        request.setOrderData(Map.of("items", List.of()));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(offlineOrderRepository.findByRestaurantIdAndDeviceIdAndClientOrderId(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(offlineOrderRepository.save(any())).thenAnswer(i -> { OfflineOrder o = i.getArgument(0); o.setId(2L); return o; });

        OfflineOrder result = offlineSyncService.queueOfflineOrder(1L, request);

        assertThat(result.getSyncStatus()).isEqualTo(OfflineSyncStatus.PENDING);
    }

    @Test @DisplayName("queueOfflineOrder — duplicate returns existing")
    void queueOfflineOrder_duplicate() {
        OfflineOrderRequest request = new OfflineOrderRequest();
        request.setDeviceId("POS-001");
        request.setClientOrderId("CLT-001");
        request.setOrderData(Map.of());
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(offlineOrderRepository.findByRestaurantIdAndDeviceIdAndClientOrderId(1L, "POS-001", "CLT-001"))
                .thenReturn(Optional.of(offlineOrder));

        OfflineOrder result = offlineSyncService.queueOfflineOrder(1L, request);

        assertThat(result.getId()).isEqualTo(1L);
        verify(offlineOrderRepository, never()).save(any());
    }

    @Test @DisplayName("syncOfflineOrder — success")
    void syncOfflineOrder_success() {
        Order syncedOrder = new Order();
        syncedOrder.setId(100L);
        when(offlineOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(posOrderService.createOrderFromOffline(eq(1L), any(), eq("CLT-001"), eq("POS-001")))
                .thenReturn(syncedOrder);

        SyncResult result = offlineSyncService.syncOfflineOrder(offlineOrder);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSyncedOrderId()).isEqualTo(100L);
    }

    @Test @DisplayName("syncOfflineOrder — failure marks as failed after max attempts")
    void syncOfflineOrder_failsAfterMaxAttempts() {
        offlineOrder.setSyncAttempts(2); // Will be incremented to 3 = MAX
        when(offlineOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(posOrderService.createOrderFromOffline(anyLong(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Sync failed"));

        SyncResult result = offlineSyncService.syncOfflineOrder(offlineOrder);

        assertThat(result.isSuccess()).isFalse();
        assertThat(offlineOrder.getSyncStatus()).isEqualTo(OfflineSyncStatus.FAILED);
    }

    @Test @DisplayName("getSyncStatus — returns status based on pending/failed counts")
    void getSyncStatus_returns() {
        when(offlineOrderRepository.countByRestaurantIdAndStatus(1L, OfflineSyncStatus.PENDING)).thenReturn(0L);
        when(offlineOrderRepository.countByRestaurantIdAndStatus(1L, OfflineSyncStatus.FAILED)).thenReturn(0L);

        OfflineSyncStatus status = offlineSyncService.getSyncStatus(1L);

        assertThat(status).isEqualTo(OfflineSyncStatus.SYNCED);
    }

    @Test @DisplayName("getDeviceStatus — returns online/offline devices")
    void getDeviceStatus_returns() {
        when(posDeviceRepository.findOnlineDevices(eq(1L), any())).thenReturn(List.of(device));
        when(posDeviceRepository.findOfflineDevices(eq(1L), any())).thenReturn(List.of());
        when(offlineOrderRepository.countByRestaurantIdAndStatus(1L, OfflineSyncStatus.PENDING)).thenReturn(2L);

        DeviceStatusResponse result = offlineSyncService.getDeviceStatus(1L);

        assertThat(result.getOnlineDevices()).hasSize(1);
        assertThat(result.getOfflineDevices()).isEmpty();
        assertThat(result.getPendingOrdersCount()).isEqualTo(2L);
    }

    @Test @DisplayName("syncDeviceOrders — syncs all pending for device")
    void syncDeviceOrders_success() {
        Order syncedOrder = new Order();
        syncedOrder.setId(100L);
        when(offlineOrderRepository.findByRestaurantIdAndDeviceIdAndSyncStatus(1L, "POS-001", OfflineSyncStatus.PENDING))
                .thenReturn(List.of(offlineOrder));
        when(offlineOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(posOrderService.createOrderFromOffline(anyLong(), any(), anyString(), anyString())).thenReturn(syncedOrder);
        when(posDeviceRepository.findByRestaurantIdAndDeviceId(1L, "POS-001")).thenReturn(Optional.of(device));

        BatchSyncResult result = offlineSyncService.syncDeviceOrders(1L, "POS-001");

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }
}
