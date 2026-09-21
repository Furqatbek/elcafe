package com.elcafe.modules.menu.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
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
class ProductCatalogIntegrationTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Full lifecycle: Category → Product → Variant")
    void fullLifecycle() {
        // Create category
        Category category = Category.builder()
                .restaurant(restaurant).name("Main Course").sortOrder(0).active(true).build();
        category = categoryRepository.save(category);

        // Create product
        Product product = Product.builder()
                .category(category).name("Steak").price(new BigDecimal("80000"))
                .status(ProductStatus.LIVE).inStock(true).build();
        product = productRepository.save(product);

        // Create variants
        ProductVariant small = ProductVariant.builder()
                .product(product).name("Small").price(new BigDecimal("60000")).inStock(true).sortOrder(0).build();
        ProductVariant large = ProductVariant.builder()
                .product(product).name("Large").price(new BigDecimal("100000")).inStock(true).sortOrder(1).build();
        small = variantRepository.save(small);
        large = variantRepository.save(large);

        em.flush();
        em.clear();

        // Verify full chain
        Product loaded = productRepository.findById(product.getId()).orElseThrow();
        assertEquals("Steak", loaded.getName());
        assertEquals(ProductStatus.LIVE, loaded.getStatus());

        List<ProductVariant> variants = variantRepository.findByProductId(product.getId());
        assertEquals(2, variants.size());
    }

    @Test
    @DisplayName("Products filtered by status and category")
    void productsByStatusAndCategory() {
        Category cat = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Drinks").sortOrder(1).active(true).build());

        productRepository.save(Product.builder()
                .category(cat).name("Coffee").price(new BigDecimal("15000"))
                .status(ProductStatus.LIVE).inStock(true).build());
        productRepository.save(Product.builder()
                .category(cat).name("Draft Beer").price(new BigDecimal("25000"))
                .status(ProductStatus.DRAFT).inStock(true).build());

        em.flush();
        em.clear();

        List<Product> live = productRepository.findByCategoryIdAndStatusOrderBySortOrder(cat.getId(), ProductStatus.LIVE);
        assertEquals(1, live.size());
        assertEquals("Coffee", live.get(0).getName());

        List<Product> all = productRepository.findByCategoryIdOrderBySortOrder(cat.getId());
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("Variant in-stock filter")
    void variantInStockFilter() {
        Category cat = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Burgers").sortOrder(0).active(true).build());
        Product burger = productRepository.save(Product.builder()
                .category(cat).name("Classic Burger").price(new BigDecimal("35000"))
                .status(ProductStatus.LIVE).inStock(true).build());

        variantRepository.save(ProductVariant.builder()
                .product(burger).name("Regular").price(new BigDecimal("35000")).inStock(true).build());
        variantRepository.save(ProductVariant.builder()
                .product(burger).name("Jumbo").price(new BigDecimal("50000")).inStock(false).build());

        em.flush();
        em.clear();

        List<ProductVariant> inStock = variantRepository.findByProductIdAndInStock(burger.getId(), true);
        assertEquals(1, inStock.size());
        assertEquals("Regular", inStock.get(0).getName());
    }

    @Test
    @DisplayName("Category active filter with sort order")
    void categoryActiveFilter() {
        categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Appetizers").sortOrder(0).active(true).build());
        categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Hidden").sortOrder(1).active(false).build());
        categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Desserts").sortOrder(2).active(true).build());

        em.flush();
        em.clear();

        List<Category> active = categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(restaurant.getId());
        assertEquals(2, active.size());
        assertEquals("Appetizers", active.get(0).getName());
        assertEquals("Desserts", active.get(1).getName());
    }
}
