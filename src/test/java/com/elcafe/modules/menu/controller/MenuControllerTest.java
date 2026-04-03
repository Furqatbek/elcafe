package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.PublicMenuCategoryDTO;
import com.elcafe.modules.menu.entity.Category;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MenuControllerTest {

    private MockMvc mockMvc;
    @Mock private MenuService menuService;
    @InjectMocks private MenuController controller;

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("GET /public/{restaurantId} — public menu") void publicMenu() throws Exception {
        PublicMenuCategoryDTO dto = PublicMenuCategoryDTO.builder().id(1L).name("Main").products(List.of()).build();
        when(menuService.getPublicMenu(1L)).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/menu/public/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
    @Test @DisplayName("GET /restaurants/{id}/categories") void categories() throws Exception {
        Category cat = new Category();
        cat.setId(1L);
        cat.setName("Main");
        when(menuService.getCategoriesByRestaurant(1L)).thenReturn(List.of(cat));
        mockMvc.perform(get("/api/v1/menu/restaurants/1/categories")).andExpect(status().isOk());
    }
}
