package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.ProductListDTO;
import com.elcafe.modules.menu.dto.PublicMenuCategoryDTO;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.service.MenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConsumerMenuControllerTest {

    private MockMvc mockMvc;
    @Mock private MenuService menuService;
    @InjectMocks private ConsumerMenuController controller;

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("GET /restaurant/{id} — full menu") void fullMenu() throws Exception {
        PublicMenuCategoryDTO dto = PublicMenuCategoryDTO.builder().id(1L).name("Main").products(List.of()).build();
        when(menuService.getPublicMenu(1L)).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/consumer/menu/restaurant/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurant/{id}/products") void products() throws Exception {
        ProductListDTO dto = ProductListDTO.builder().id(1L).name("Steak").build();
        when(menuService.getProductsByRestaurant(1L)).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/consumer/menu/restaurant/1/products")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurant/{id}/categories") void categories() throws Exception {
        Category cat = new Category(); cat.setId(1L); cat.setName("Main");
        when(menuService.getCategoriesByRestaurant(1L)).thenReturn(List.of(cat));
        mockMvc.perform(get("/api/v1/consumer/menu/restaurant/1/categories")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /category/{id}/products") void categoryProducts() throws Exception {
        Product p = new Product(); p.setId(1L); p.setName("Steak"); p.setPrice(new BigDecimal("80000"));
        when(menuService.getProductsByCategory(1L)).thenReturn(List.of(p));
        mockMvc.perform(get("/api/v1/consumer/menu/category/1/products")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /product/{id}") void productById() throws Exception {
        Product p = new Product(); p.setId(1L); p.setName("Steak");
        when(menuService.getProductById(1L)).thenReturn(p);
        mockMvc.perform(get("/api/v1/consumer/menu/product/1")).andExpect(status().isOk());
    }
}
