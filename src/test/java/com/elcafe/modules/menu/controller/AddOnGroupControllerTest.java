package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.AddOnGroupResponse;
import com.elcafe.modules.menu.dto.CreateAddOnGroupRequest;
import com.elcafe.modules.menu.dto.UpdateAddOnGroupRequest;
import com.elcafe.modules.menu.service.AddOnGroupService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AddOnGroupControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private AddOnGroupService addOnGroupService;
    @InjectMocks private AddOnGroupController controller;
    private AddOnGroupResponse response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        response = AddOnGroupResponse.builder().id(1L).restaurantId(1L).name("Extras")
                .active(true).addOns(List.of()).build();
    }

    @Test @DisplayName("GET / — all groups") void getAll() throws Exception {
        when(addOnGroupService.getAllAddOnGroupsByRestaurant(1L)).thenReturn(List.of(response));
        mockMvc.perform(get("/api/v1/restaurants/1/addon-groups"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(addOnGroupService.getAddOnGroupById(1L, 1L)).thenReturn(response);
        mockMvc.perform(get("/api/v1/restaurants/1/addon-groups/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Extras"));
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateAddOnGroupRequest req = new CreateAddOnGroupRequest();
        req.setRestaurantId(1L); req.setName("Sauces"); req.setMinSelection(0); req.setMaxSelection(2);
        when(addOnGroupService.createAddOnGroup(any())).thenReturn(response);
        mockMvc.perform(post("/api/v1/restaurants/1/addon-groups").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        UpdateAddOnGroupRequest req = new UpdateAddOnGroupRequest();
        req.setName("Updated");
        when(addOnGroupService.updateAddOnGroup(eq(1L), eq(1L), any())).thenReturn(response);
        mockMvc.perform(put("/api/v1/restaurants/1/addon-groups/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteGroup() throws Exception {
        mockMvc.perform(delete("/api/v1/restaurants/1/addon-groups/1")).andExpect(status().isOk());
        verify(addOnGroupService).deleteAddOnGroup(1L, 1L);
    }
}
