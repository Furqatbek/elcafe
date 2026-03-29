package com.elcafe.modules.waiter.controller;

import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.waiter.dto.CreateWaiterRequest;
import com.elcafe.modules.waiter.dto.UpdateWaiterRequest;
import com.elcafe.modules.waiter.dto.WaiterAuthRequest;
import com.elcafe.modules.waiter.dto.WaiterAuthResponse;
import com.elcafe.modules.waiter.dto.WaiterResponse;
import com.elcafe.modules.waiter.enums.WaiterRole;
import com.elcafe.modules.waiter.service.WaiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createTable;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createWaiterRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaiterControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private WaiterService waiterService;
    @InjectMocks private WaiterController waiterController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(waiterController).build();
    }

    private WaiterResponse buildResponse(Long id, String name) {
        return WaiterResponse.builder()
                .id(id).name(name).role(WaiterRole.WAITER).active(true)
                .activeTablesCount(0).build();
    }

    @Test
    @DisplayName("POST /auth - authenticate returns 200")
    void authenticate_returns200() throws Exception {
        WaiterAuthResponse authResponse = WaiterAuthResponse.builder()
                .waiterId(1L).name("Ali").token("jwt-token").build();
        when(waiterService.authenticate(any(WaiterAuthRequest.class))).thenReturn(authResponse);

        WaiterAuthRequest request = new WaiterAuthRequest();
        request.setPinCode("1234");

        mockMvc.perform(post("/api/v1/waiters/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    @DisplayName("GET / - returns paginated waiters")
    void getAllWaiters_returns200() throws Exception {
        Page<WaiterResponse> page = new PageImpl<>(List.of(buildResponse(1L, "Ali")));
        when(waiterService.getAllWaiters(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/waiters"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /active - returns active waiters")
    void getActiveWaiters_returns200() throws Exception {
        when(waiterService.getActiveWaiters()).thenReturn(List.of(buildResponse(1L, "Ali")));

        mockMvc.perform(get("/api/v1/waiters/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Ali"));
    }

    @Test
    @DisplayName("GET /{id} - returns waiter")
    void getWaiterById_returns200() throws Exception {
        when(waiterService.getById(1L)).thenReturn(buildResponse(1L, "Ali"));

        mockMvc.perform(get("/api/v1/waiters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ali"));
    }

    @Test
    @DisplayName("GET /me - returns profile with X-Waiter-Id header")
    void getMyProfile_returns200() throws Exception {
        when(waiterService.getById(1L)).thenReturn(buildResponse(1L, "Ali"));

        mockMvc.perform(get("/api/v1/waiters/me")
                        .header("X-Waiter-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ali"));
    }

    @Test
    @DisplayName("GET /me/tables - returns assigned tables")
    void getMyTables_returns200() throws Exception {
        RestaurantTable table = createTable();
        when(waiterService.getActiveTables(1L)).thenReturn(List.of(table));

        mockMvc.perform(get("/api/v1/waiters/me/tables")
                        .header("X-Waiter-Id", 1L))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST / - creates waiter returns 201")
    void createWaiter_returns201() throws Exception {
        CreateWaiterRequest request = createWaiterRequest("Ali", "1234");
        when(waiterService.createWaiter(any())).thenReturn(buildResponse(1L, "Ali"));

        mockMvc.perform(post("/api/v1/waiters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Ali"));
    }

    @Test
    @DisplayName("PUT /{id} - updates waiter")
    void updateWaiter_returns200() throws Exception {
        UpdateWaiterRequest request = new UpdateWaiterRequest();
        request.setName("Updated");
        when(waiterService.updateWaiter(eq(1L), any())).thenReturn(buildResponse(1L, "Updated"));

        mockMvc.perform(put("/api/v1/waiters/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"));
    }

    @Test
    @DisplayName("DELETE /{id} - deletes waiter")
    void deleteWaiter_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/waiters/1"))
                .andExpect(status().isOk());
        verify(waiterService).deleteWaiter(1L);
    }

    @Test
    @DisplayName("POST /{waiterId}/tables/{tableId}/assign - assigns table")
    void assignToTable_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/waiters/1/tables/5/assign"))
                .andExpect(status().isOk());
        verify(waiterService).assignToTable(1L, 5L);
    }

    @Test
    @DisplayName("POST /{waiterId}/tables/{tableId}/unassign - unassigns table")
    void unassignFromTable_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/waiters/1/tables/5/unassign"))
                .andExpect(status().isOk());
        verify(waiterService).unassignFromTable(1L, 5L);
    }
}
