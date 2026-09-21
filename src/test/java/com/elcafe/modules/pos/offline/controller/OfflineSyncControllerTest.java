package com.elcafe.modules.pos.offline.controller;

import com.elcafe.modules.pos.offline.dto.*;
import com.elcafe.modules.pos.offline.entity.OfflineOrder;
import com.elcafe.modules.pos.offline.entity.POSDevice;
import com.elcafe.modules.pos.offline.enums.OfflineSyncStatus;
import com.elcafe.modules.pos.offline.service.OfflineSyncService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OfflineSyncControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private OfflineSyncService offlineSyncService;
    @InjectMocks private OfflineSyncController controller;
    private final String BASE = "/api/v1/restaurants/1/pos/offline";

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("POST /devices/register") void register() throws Exception {
        DeviceRegistrationRequest req = new DeviceRegistrationRequest(); req.setDeviceId("POS-001");
        Restaurant r = new Restaurant(); r.setId(1L);
        when(offlineSyncService.registerDevice(eq(1L), any()))
                .thenReturn(POSDevice.builder().id(1L).restaurant(r).deviceId("POS-001").isActive(true).build());
        mockMvc.perform(post(BASE + "/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /devices/{id}/heartbeat") void heartbeat() throws Exception {
        mockMvc.perform(post(BASE + "/devices/POS-001/heartbeat")).andExpect(status().isOk());
        verify(offlineSyncService).recordHeartbeat(1L, "POS-001");
    }
    @Test @DisplayName("GET /devices/status") void deviceStatus() throws Exception {
        when(offlineSyncService.getDeviceStatus(1L))
                .thenReturn(DeviceStatusResponse.builder().onlineDevices(List.of()).offlineDevices(List.of()).pendingOrdersCount(0L).build());
        mockMvc.perform(get(BASE + "/devices/status")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /orders") void queueOrder() throws Exception {
        OfflineOrderRequest req = new OfflineOrderRequest();
        req.setDeviceId("POS-001"); req.setClientOrderId("CLT-001"); req.setOrderData(Map.of("items", List.of()));
        Restaurant r = new Restaurant(); r.setId(1L);
        when(offlineSyncService.queueOfflineOrder(eq(1L), any()))
                .thenReturn(OfflineOrder.builder().id(1L).restaurant(r).deviceId("POS-001")
                        .clientOrderId("CLT-001").syncStatus(OfflineSyncStatus.PENDING).build());
        mockMvc.perform(post(BASE + "/orders").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /orders/batch") void batchQueue() throws Exception {
        when(offlineSyncService.queueOfflineOrders(eq(1L), any())).thenReturn(List.of());
        mockMvc.perform(post(BASE + "/orders/batch").contentType(MediaType.APPLICATION_JSON)
                .content("[]")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /devices/{id}/sync") void sync() throws Exception {
        when(offlineSyncService.syncDeviceOrders(1L, "POS-001"))
                .thenReturn(BatchSyncResult.of(List.of()));
        mockMvc.perform(post(BASE + "/devices/POS-001/sync")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /status") void syncStatus() throws Exception {
        when(offlineSyncService.getSyncStatus(1L)).thenReturn(OfflineSyncStatus.SYNCED);
        mockMvc.perform(get(BASE + "/status")).andExpect(status().isOk());
    }
}
