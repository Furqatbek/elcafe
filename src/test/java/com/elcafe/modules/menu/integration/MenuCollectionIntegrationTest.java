package com.elcafe.modules.menu.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.MenuCollection;
import com.elcafe.modules.menu.entity.MenuCollectionItem;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.MenuCollectionRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class MenuCollectionIntegrationTest {

    @Autowired private MenuCollectionRepository collectionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        Category cat = Category.builder()
                .restaurant(restaurant).name("Main").sortOrder(0).active(true).build();
        em.persist(cat);

        product1 = Product.builder()
                .category(cat).name("Steak").price(new BigDecimal("80000"))
                .status(ProductStatus.LIVE).inStock(true).build();
        em.persist(product1);

        product2 = Product.builder()
                .category(cat).name("Salmon").price(new BigDecimal("90000"))
                .status(ProductStatus.LIVE).inStock(true).build();
        em.persist(product2);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Collection lifecycle: create → add products → verify items")
    void collectionLifecycle() {
        Product p1 = productRepository.findById(product1.getId()).orElseThrow();
        Product p2 = productRepository.findById(product2.getId()).orElseThrow();

        MenuCollection collection = MenuCollection.builder()
                .restaurant(restaurant).name("Weekend Specials").isActive(true).sortOrder(0).build();

        MenuCollectionItem item1 = MenuCollectionItem.builder()
                .menuCollection(collection).product(p1).sortOrder(0).isFeatured(true).build();
        MenuCollectionItem item2 = MenuCollectionItem.builder()
                .menuCollection(collection).product(p2).sortOrder(1).isFeatured(false).build();

        collection.setItems(new ArrayList<>(List.of(item1, item2)));
        collection = collectionRepository.save(collection);

        em.flush();
        em.clear();

        MenuCollection loaded = collectionRepository.findById(collection.getId()).orElseThrow();
        assertEquals("Weekend Specials", loaded.getName());
        assertEquals(2, loaded.getItems().size());
        assertTrue(loaded.getItems().stream().anyMatch(i -> i.getProduct().getName().equals("Steak")));
    }

    @Test
    @DisplayName("Active collections filtered by date range")
    void activeCollectionsByDate() {
        // Active now
        collectionRepository.save(MenuCollection.builder()
                .restaurant(restaurant).name("Current Special").isActive(true)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
                .sortOrder(0).build());

        // Expired
        collectionRepository.save(MenuCollection.builder()
                .restaurant(restaurant).name("Old Special").isActive(true)
                .startDate(LocalDate.of(2025, 1, 1)).endDate(LocalDate.of(2025, 12, 31))
                .sortOrder(1).build());

        // Inactive
        collectionRepository.save(MenuCollection.builder()
                .restaurant(restaurant).name("Disabled").isActive(false)
                .sortOrder(2).build());

        em.flush();
        em.clear();

        List<MenuCollection> active = collectionRepository.findActiveMenuCollections(
                restaurant.getId(), LocalDate.of(2026, 4, 1));
        assertEquals(1, active.size());
        assertEquals("Current Special", active.get(0).getName());
    }

    @Test
    @DisplayName("Collection with no date range is always active")
    void noDateRangeAlwaysActive() {
        collectionRepository.save(MenuCollection.builder()
                .restaurant(restaurant).name("Evergreen").isActive(true).sortOrder(0).build());

        em.flush();
        em.clear();

        List<MenuCollection> active = collectionRepository.findActiveMenuCollections(
                restaurant.getId(), LocalDate.of(2026, 4, 1));
        assertEquals(1, active.size());
    }

    @Test
    @DisplayName("Remove product from collection via orphanRemoval")
    void removeProduct() {
        Product p1 = productRepository.findById(product1.getId()).orElseThrow();
        MenuCollection collection = MenuCollection.builder()
                .restaurant(restaurant).name("Test").isActive(true).sortOrder(0).build();
        MenuCollectionItem item = MenuCollectionItem.builder()
                .menuCollection(collection).product(p1).sortOrder(0).isFeatured(false).build();
        collection.setItems(new ArrayList<>(List.of(item)));
        collection = collectionRepository.save(collection);
        em.flush();
        em.clear();

        MenuCollection loaded = collectionRepository.findById(collection.getId()).orElseThrow();
        assertEquals(1, loaded.getItems().size());

        loaded.getItems().clear();
        collectionRepository.save(loaded);
        em.flush();
        em.clear();

        MenuCollection afterRemoval = collectionRepository.findById(collection.getId()).orElseThrow();
        assertEquals(0, afterRemoval.getItems().size());
    }
}
