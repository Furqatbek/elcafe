package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.CreateIngredientRequest;
import com.elcafe.modules.menu.dto.IngredientDTO;
import com.elcafe.modules.menu.dto.UpdateIngredientRequest;
import com.elcafe.modules.menu.service.IngredientService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class IngredientControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private IngredientService ingredientService;
    @InjectMocks private IngredientController controller;
    private IngredientDTO dto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        dto = IngredientDTO.builder().id(1L).name("Flour").unit("kg")
                .costPerUnit(new BigDecimal("5000")).isActive(true).build();
    }

    @Test @DisplayName("GET / — paginated list") void getAll() throws Exception {
        when(ingredientService.getAllIngredients(any())).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/ingredients")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(ingredientService.getIngredientById(1L)).thenReturn(dto);
        mockMvc.perform(get("/api/v1/ingredients/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Flour"));
    }
    @Test @DisplayName("GET /search") void search() throws Exception {
        when(ingredientService.searchIngredients(eq("Flour"), any(), any())).thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/ingredients/search").param("query", "Flour"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /low-stock") void lowStock() throws Exception {
        when(ingredientService.getLowStockIngredients()).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/ingredients/low-stock")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateIngredientRequest req = new CreateIngredientRequest();
        req.setName("Flour"); req.setUnit("kg"); req.setCostPerUnit(new BigDecimal("5000"));
        when(ingredientService.createIngredient(any())).thenReturn(dto);
        mockMvc.perform(post("/api/v1/ingredients").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        UpdateIngredientRequest req = new UpdateIngredientRequest();
        req.setName("Updated");
        when(ingredientService.updateIngredient(eq(1L), any())).thenReturn(dto);
        mockMvc.perform(put("/api/v1/ingredients/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteIngredient() throws Exception {
        mockMvc.perform(delete("/api/v1/ingredients/1")).andExpect(status().isOk());
        verify(ingredientService).deleteIngredient(1L);
    }
}
