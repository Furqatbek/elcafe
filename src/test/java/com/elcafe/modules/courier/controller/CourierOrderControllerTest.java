package com.elcafe.modules.courier.controller;

import com.elcafe.modules.courier.dto.CourierLocationResponse;
import com.elcafe.modules.courier.dto.CourierLocationUpdateRequest;
import com.elcafe.modules.courier.service.CourierLocationService;
import com.elcafe.modules.courier.service.CourierOrderService;
import com.elcafe.modules.order.entity.Order;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourierOrderControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private CourierOrderService courierOrderService;
    @Mock private CourierLocationService courierLocationService;
    @InjectMocks private CourierOrderController controller;
    private final String BASE = "/api/v1/courier/orders";
    private Order order;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        order = new Order(); order.setId(1L); order.setOrderNumber("ORD-001");
    }

    // Order management (7)
    @Test @DisplayName("GET /available") void available() throws Exception {
        when(courierOrderService.getAvailableOrders(isNull())).thenReturn(List.of(order));
        mockMvc.perform(get(BASE + "/available")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /my-orders") void myOrders() throws Exception {
        when(courierOrderService.getCourierOrders(1L)).thenReturn(List.of(order));
        mockMvc.perform(get(BASE + "/my-orders").param("courierId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /{id}/accept") void accept() throws Exception {
        when(courierOrderService.acceptOrder(1L, 1L)).thenReturn(order);
        mockMvc.perform(post(BASE + "/1/accept").param("courierId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /{id}/decline") void decline() throws Exception {
        mockMvc.perform(post(BASE + "/1/decline").param("courierId", "1")).andExpect(status().isOk());
        verify(courierOrderService).declineOrder(eq(1L), eq(1L), isNull());
    }
    @Test @DisplayName("POST /assign") void assign() throws Exception {
        when(courierOrderService.assignCourier(1L, 1L)).thenReturn(order);
        mockMvc.perform(post(BASE + "/assign").param("orderId", "1").param("courierId", "1"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("POST /{id}/start-delivery") void startDelivery() throws Exception {
        when(courierOrderService.startDelivery(1L, 1L)).thenReturn(order);
        mockMvc.perform(post(BASE + "/1/start-delivery").param("courierId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /{id}/complete") void complete() throws Exception {
        when(courierOrderService.completeDelivery(1L, 1L, null)).thenReturn(order);
        mockMvc.perform(post(BASE + "/1/complete").param("courierId", "1")).andExpect(status().isOk());
    }

    // Location tracking (5)
    @Test @DisplayName("POST /location") void updateLocation() throws Exception {
        CourierLocationUpdateRequest req = new CourierLocationUpdateRequest();
        req.setLatitude(41.31); req.setLongitude(69.24);
        when(courierLocationService.updateLocation(eq(1L), any())).thenReturn(
                CourierLocationResponse.builder().id(1L).latitude(41.31).longitude(69.24).build());
        mockMvc.perform(post(BASE + "/location").param("courierId", "1")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /location/{id}") void getCourierLocation() throws Exception {
        when(courierLocationService.getLatestLocation(1L)).thenReturn(
                CourierLocationResponse.builder().id(1L).build());
        mockMvc.perform(get(BASE + "/location/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}/location") void getOrderLocation() throws Exception {
        when(courierLocationService.getOrderLocation(1L)).thenReturn(
                CourierLocationResponse.builder().id(1L).build());
        mockMvc.perform(get(BASE + "/1/location")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}/route") void getRoute() throws Exception {
        when(courierLocationService.getOrderRoute(1L)).thenReturn(List.of());
        mockMvc.perform(get(BASE + "/1/route")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /location/active") void activeLocations() throws Exception {
        when(courierLocationService.getActiveCourierLocations()).thenReturn(List.of());
        mockMvc.perform(get(BASE + "/location/active")).andExpect(status().isOk());
    }
}
