package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.CreateMenuCollectionRequest;
import com.elcafe.modules.menu.dto.MenuCollectionDTO;
import com.elcafe.modules.menu.dto.UpdateMenuCollectionRequest;
import com.elcafe.modules.menu.entity.MenuCollection;
import com.elcafe.modules.menu.entity.MenuCollectionItem;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.MenuCollectionItemRepository;
import com.elcafe.modules.menu.repository.MenuCollectionRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuCollectionServiceTest {

    @Mock private MenuCollectionRepository menuCollectionRepository;
    @Mock private MenuCollectionItemRepository menuCollectionItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private MenuCollectionService menuCollectionService;

    private Restaurant restaurant;
    private MenuCollection collection;
    private Product product;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        product = new Product();
        product.setId(1L);
        product.setName("Steak");

        collection = MenuCollection.builder()
                .id(1L).restaurant(restaurant).name("Specials")
                .description("Today's specials").isActive(true)
                .sortOrder(0).build();
        collection.setItems(new ArrayList<>());
    }

    @Test @DisplayName("getCollections — returns paginated")
    void getCollections_returnsPage() {
        when(menuCollectionRepository.findByRestaurant_Id(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(collection)));

        Page<MenuCollectionDTO> result = menuCollectionService.getMenuCollections(1L, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Specials");
    }

    @Test @DisplayName("getActive — returns only active collections")
    void getActive_returnsList() {
        when(menuCollectionRepository.findActiveMenuCollections(eq(1L), any(LocalDate.class)))
                .thenReturn(List.of(collection));

        List<MenuCollectionDTO> result = menuCollectionService.getActiveMenuCollections(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
    }

    @Test @DisplayName("getById — found")
    void getById_found() {
        when(menuCollectionRepository.findById(1L)).thenReturn(Optional.of(collection));

        MenuCollectionDTO result = menuCollectionService.getMenuCollectionById(1L);

        assertThat(result.getName()).isEqualTo("Specials");
    }

    @Test @DisplayName("getById — not found throws")
    void getById_notFound_throws() {
        when(menuCollectionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuCollectionService.getMenuCollectionById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Menu collection not found");
    }

    @Test @DisplayName("create — success")
    void create_success() {
        CreateMenuCollectionRequest request = new CreateMenuCollectionRequest();
        request.setRestaurantId(1L);
        request.setName("Weekend Specials");
        request.setDescription("Weekend deals");

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(menuCollectionRepository.save(any(MenuCollection.class))).thenAnswer(i -> {
            MenuCollection saved = i.getArgument(0);
            saved.setId(2L);
            saved.setItems(new ArrayList<>());
            return saved;
        });
        when(menuCollectionRepository.findById(2L)).thenAnswer(i -> {
            MenuCollection mc = MenuCollection.builder()
                    .id(2L).restaurant(restaurant).name("Weekend Specials")
                    .isActive(true).build();
            mc.setItems(new ArrayList<>());
            return Optional.of(mc);
        });

        MenuCollectionDTO result = menuCollectionService.createMenuCollection(request);

        assertThat(result.getName()).isEqualTo("Weekend Specials");
    }

    @Test @DisplayName("update — success")
    void update_success() {
        UpdateMenuCollectionRequest request = new UpdateMenuCollectionRequest();
        request.setName("Updated Specials");
        request.setIsActive(false);

        when(menuCollectionRepository.findById(1L)).thenReturn(Optional.of(collection));
        when(menuCollectionRepository.save(any(MenuCollection.class))).thenAnswer(i -> i.getArgument(0));

        MenuCollectionDTO result = menuCollectionService.updateMenuCollection(1L, request);

        assertThat(result.getName()).isEqualTo("Updated Specials");
        assertThat(result.getIsActive()).isFalse();
    }

    @Test @DisplayName("addProducts — adds products to collection")
    void addProducts_success() {
        when(menuCollectionRepository.findById(1L)).thenReturn(Optional.of(collection));
        when(menuCollectionItemRepository.existsByMenuCollectionIdAndProductId(1L, 1L)).thenReturn(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(menuCollectionItemRepository.save(any(MenuCollectionItem.class))).thenAnswer(i -> i.getArgument(0));

        menuCollectionService.addProductsToCollection(1L, List.of(1L));

        verify(menuCollectionItemRepository).save(any(MenuCollectionItem.class));
    }

    @Test @DisplayName("delete — success")
    void delete_success() {
        when(menuCollectionRepository.findById(1L)).thenReturn(Optional.of(collection));

        menuCollectionService.deleteMenuCollection(1L);

        verify(menuCollectionRepository).delete(collection);
    }
}
