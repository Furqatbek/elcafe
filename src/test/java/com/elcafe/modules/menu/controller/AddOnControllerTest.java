package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.AddOnResponse;
import com.elcafe.modules.menu.dto.CreateAddOnRequest;
import com.elcafe.modules.menu.dto.UpdateAddOnRequest;
import com.elcafe.modules.menu.service.AddOnService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AddOnControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private AddOnService addOnService;
    @InjectMocks private AddOnController controller;
    private AddOnResponse response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        response = AddOnResponse.builder().id(1L).addOnGroupId(1L).name("Cheese")
                .price(new BigDecimal("5000")).available(true).build();
    }

    @Test @DisplayName("GET / — all add-ons") void getAll() throws Exception {
        when(addOnService.getAllAddOnsByGroup(1L)).thenReturn(List.of(response));
        mockMvc.perform(get("/api/v1/addon-groups/1/addons"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(addOnService.getAddOnById(1L, 1L)).thenReturn(response);
        mockMvc.perform(get("/api/v1/addon-groups/1/addons/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Cheese"));
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateAddOnRequest req = new CreateAddOnRequest();
        req.setAddOnGroupId(1L); req.setName("Bacon"); req.setPrice(new BigDecimal("8000"));
        when(addOnService.createAddOn(any())).thenReturn(response);
        mockMvc.perform(post("/api/v1/addon-groups/1/addons").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        UpdateAddOnRequest req = new UpdateAddOnRequest();
        req.setName("Updated");
        when(addOnService.updateAddOn(eq(1L), eq(1L), any())).thenReturn(response);
        mockMvc.perform(put("/api/v1/addon-groups/1/addons/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteAddOn() throws Exception {
        mockMvc.perform(delete("/api/v1/addon-groups/1/addons/1")).andExpect(status().isOk());
        verify(addOnService).deleteAddOn(1L, 1L);
    }
}
