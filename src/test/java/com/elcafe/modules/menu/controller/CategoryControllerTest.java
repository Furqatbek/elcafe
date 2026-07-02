package com.elcafe.modules.menu.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.kitchen.repository.KitchenStationRepository;
import com.elcafe.modules.menu.dto.CreateCategoryRequest;
import com.elcafe.modules.menu.dto.UpdateCategoryRequest;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.service.MenuService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private MenuService menuService;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private KitchenStationRepository kitchenStationRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private CategoryController controller;
    private Restaurant restaurant;
    private Category category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");
        restaurant.setActive(true);
        category = new Category();
        category.setId(1L);
        category.setName("Main Course");
        category.setActive(true);
        category.setRestaurant(restaurant); // update/delete ownership read category.getRestaurant()
        when(menuService.getCategoryById(1L)).thenReturn(category); // delete loads it to check owner
    }

    @Test @DisplayName("GET / — active categories") void getActive() throws Exception {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuService.getActiveCategoriesByRestaurant(1L)).thenReturn(List.of(category));
        mockMvc.perform(get("/api/v1/categories").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(menuService.getCategoryById(1L)).thenReturn(category);
        mockMvc.perform(get("/api/v1/categories/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setRestaurantId(1L); req.setName("Desserts");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuService.createCategory(any(Category.class))).thenReturn(category);
        mockMvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        UpdateCategoryRequest req = new UpdateCategoryRequest();
        req.setName("Updated");
        when(menuService.getCategoryById(1L)).thenReturn(category);
        when(menuService.updateCategory(eq(1L), any(Category.class))).thenReturn(category);
        mockMvc.perform(put("/api/v1/categories/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteCategory() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1")).andExpect(status().isOk());
        verify(menuService).deleteCategory(1L);
    }
}
