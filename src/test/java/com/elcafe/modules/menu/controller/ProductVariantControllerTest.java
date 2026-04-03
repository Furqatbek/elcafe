package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.CreateProductVariantRequest;
import com.elcafe.modules.menu.dto.ProductVariantResponse;
import com.elcafe.modules.menu.dto.UpdateProductVariantRequest;
import com.elcafe.modules.menu.service.ProductVariantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
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
class ProductVariantControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ProductVariantService productVariantService;
    @InjectMocks private ProductVariantController controller;
    private ProductVariantResponse response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        response = ProductVariantResponse.builder().id(1L).productId(1L).name("Large")
                .price(new BigDecimal("35000")).inStock(true).build();
    }

    @Test @DisplayName("GET / — paginated") void getPaginated() throws Exception {
        when(productVariantService.getAllVariantsByProduct(eq(1L), any())).thenReturn(new PageImpl<>(List.of(response)));
        mockMvc.perform(get("/api/v1/products/1/variants")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /all") void getAll() throws Exception {
        when(productVariantService.getAllVariantsByProduct(1L)).thenReturn(List.of(response));
        mockMvc.perform(get("/api/v1/products/1/variants/all")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{variantId}") void getById() throws Exception {
        when(productVariantService.getVariantById(1L, 1L)).thenReturn(response);
        mockMvc.perform(get("/api/v1/products/1/variants/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Large"));
    }
    @Test @DisplayName("GET /search") void search() throws Exception {
        when(productVariantService.searchVariants(eq(1L), eq("Large"), any())).thenReturn(new PageImpl<>(List.of(response)));
        mockMvc.perform(get("/api/v1/products/1/variants/search").param("query", "Large"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /in-stock") void inStock() throws Exception {
        when(productVariantService.getInStockVariants(1L)).thenReturn(List.of(response));
        mockMvc.perform(get("/api/v1/products/1/variants/in-stock")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateProductVariantRequest req = new CreateProductVariantRequest();
        req.setName("Small"); req.setPrice(new BigDecimal("25000"));
        when(productVariantService.createVariant(eq(1L), any())).thenReturn(response);
        mockMvc.perform(post("/api/v1/products/1/variants").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{variantId}") void update() throws Exception {
        UpdateProductVariantRequest req = new UpdateProductVariantRequest();
        req.setName("XL");
        when(productVariantService.updateVariant(eq(1L), eq(1L), any())).thenReturn(response);
        mockMvc.perform(put("/api/v1/products/1/variants/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{variantId}") void deleteVariant() throws Exception {
        mockMvc.perform(delete("/api/v1/products/1/variants/1")).andExpect(status().isOk());
        verify(productVariantService).deleteVariant(1L, 1L);
    }
}
