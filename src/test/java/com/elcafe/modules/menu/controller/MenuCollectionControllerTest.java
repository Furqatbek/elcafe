package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.CreateMenuCollectionRequest;
import com.elcafe.modules.menu.dto.MenuCollectionDTO;
import com.elcafe.modules.menu.dto.UpdateMenuCollectionRequest;
import com.elcafe.modules.menu.service.MenuCollectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
class MenuCollectionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock private MenuCollectionService menuCollectionService;
    @InjectMocks private MenuCollectionController controller;
    private MenuCollectionDTO dto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        dto = MenuCollectionDTO.builder().id(1L).restaurantId(1L).name("Specials")
                .isActive(true).items(List.of()).build();
    }

    @Test @DisplayName("GET / — paginated") void getPaginated() throws Exception {
        when(menuCollectionService.getMenuCollections(eq(1L), any())).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/menu-collections").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(menuCollectionService.getMenuCollectionById(1L)).thenReturn(dto);
        mockMvc.perform(get("/api/v1/menu-collections/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Specials"));
    }
    @Test @DisplayName("GET /active") void getActive() throws Exception {
        when(menuCollectionService.getActiveMenuCollections(1L)).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/menu-collections/active").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateMenuCollectionRequest req = new CreateMenuCollectionRequest();
        req.setRestaurantId(1L); req.setName("New Collection");
        when(menuCollectionService.createMenuCollection(any())).thenReturn(dto);
        mockMvc.perform(post("/api/v1/menu-collections").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        UpdateMenuCollectionRequest req = new UpdateMenuCollectionRequest();
        req.setName("Updated");
        when(menuCollectionService.updateMenuCollection(eq(1L), any())).thenReturn(dto);
        mockMvc.perform(put("/api/v1/menu-collections/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /{id}/products") void addProducts() throws Exception {
        mockMvc.perform(post("/api/v1/menu-collections/1/products").contentType(MediaType.APPLICATION_JSON)
                .content("[1,2,3]")).andExpect(status().isOk());
        verify(menuCollectionService).addProductsToCollection(eq(1L), any());
    }
    @Test @DisplayName("DELETE /{id}") void deleteCollection() throws Exception {
        mockMvc.perform(delete("/api/v1/menu-collections/1")).andExpect(status().isOk());
        verify(menuCollectionService).deleteMenuCollection(1L);
    }
}
