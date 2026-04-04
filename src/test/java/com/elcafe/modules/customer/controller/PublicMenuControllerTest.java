package com.elcafe.modules.customer.controller;

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
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicMenuControllerTest {
    private MockMvc mockMvc;
    @Mock private MenuService menuService;
    @InjectMocks private PublicMenuController controller;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("GET /restaurants/{id}/categories") void categories() throws Exception {
        when(menuService.getActiveCategoriesByRestaurant(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/customer/public/restaurants/1/categories")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /categories/{id}/products") void products() throws Exception {
        when(menuService.getProductsByCategory(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/customer/public/categories/1/products")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurants/{id}/menu") void menu() throws Exception {
        when(menuService.getPublicMenu(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/customer/public/restaurants/1/menu")).andExpect(status().isOk());
    }
}
