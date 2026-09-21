package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.CreateProductRequest;
import com.elcafe.modules.menu.dto.ProductListDTO;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.service.MenuService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private MenuService menuService;
    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private ProductController controller;
    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        category = new Category();
        category.setId(1L);
        category.setName("Main");
        product = new Product();
        product.setId(1L);
        product.setName("Steak");
        product.setPrice(new BigDecimal("80000"));
        product.setStatus(ProductStatus.LIVE);
    }

    @Test @DisplayName("POST / — create") void create() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setCategoryId(1L); req.setName("Steak"); req.setPrice(new BigDecimal("80000"));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(menuService.createProduct(any(Product.class))).thenReturn(product);
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(menuService.getProductById(1L)).thenReturn(product);
        mockMvc.perform(get("/api/v1/products/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurant/{id}") void getByRestaurant() throws Exception {
        ProductListDTO dto = ProductListDTO.builder().id(1L).name("Steak").build();
        when(menuService.getProductsByRestaurant(1L)).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/products/restaurant/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /category/{id}") void getByCategory() throws Exception {
        when(menuService.getProductsByCategory(1L)).thenReturn(List.of(product));
        mockMvc.perform(get("/api/v1/products/category/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setCategoryId(1L); req.setName("Updated"); req.setPrice(new BigDecimal("90000"));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(menuService.updateProduct(eq(1L), any(Product.class))).thenReturn(product);
        mockMvc.perform(put("/api/v1/products/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("PATCH /{id}/toggle-status") void toggleStatus() throws Exception {
        when(menuService.toggleProductStatus(1L)).thenReturn(product);
        mockMvc.perform(patch("/api/v1/products/1/toggle-status")).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteProduct() throws Exception {
        mockMvc.perform(delete("/api/v1/products/1")).andExpect(status().isOk());
        verify(menuService).deleteProduct(1L);
    }
}
