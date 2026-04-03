package com.elcafe.modules.menu.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class ProductRepositoryTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Category category;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test"); restaurant.setAddress("123 St"); restaurant.setActive(true);
        em.persist(restaurant);
        category = Category.builder().restaurant(restaurant).name("Main").sortOrder(0).active(true).build();
        em.persist(category);
        em.persist(Product.builder().category(category).name("Steak").price(new BigDecimal("80000"))
                .status(ProductStatus.LIVE).inStock(true).sortOrder(0).build());
        em.persist(Product.builder().category(category).name("Salad").price(new BigDecimal("30000"))
                .status(ProductStatus.DRAFT).inStock(true).sortOrder(1).build());
        em.persist(Product.builder().category(category).name("Soup").price(new BigDecimal("20000"))
                .status(ProductStatus.LIVE).inStock(false).sortOrder(2).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByRestaurant_IdAndStatus — filters by status")
    void byStatus() {
        List<Product> live = productRepository.findByRestaurant_IdAndStatus(restaurant.getId(), ProductStatus.LIVE);
        assertEquals(2, live.size());
    }

    @Test @DisplayName("findByCategoryIdOrderBySortOrder — sorted by category")
    void byCategory() {
        List<Product> products = productRepository.findByCategoryIdOrderBySortOrder(category.getId());
        assertEquals(3, products.size());
        assertEquals("Steak", products.get(0).getName());
    }

    @Test @DisplayName("findByRestaurant_Id — returns all products")
    void allByRestaurant() {
        List<Product> all = productRepository.findByRestaurant_Id(restaurant.getId());
        assertEquals(3, all.size());
    }

    @Test @DisplayName("findByCategoryIdAndRestaurantIdAndStatus — cross-validates restaurant")
    void byCategoryAndRestaurantAndStatus() {
        List<Product> result = productRepository.findByCategoryIdAndRestaurantIdAndStatus(
                category.getId(), restaurant.getId(), ProductStatus.LIVE);
        assertEquals(2, result.size());
    }
}
