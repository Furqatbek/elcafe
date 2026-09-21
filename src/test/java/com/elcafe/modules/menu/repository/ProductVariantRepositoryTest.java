package com.elcafe.modules.menu.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
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
class ProductVariantRepositoryTest {

    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private EntityManager em;

    private Product product;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Test"); restaurant.setAddress("123 St"); restaurant.setActive(true);
        em.persist(restaurant);
        Category cat = Category.builder().restaurant(restaurant).name("Main").sortOrder(0).active(true).build();
        em.persist(cat);
        product = Product.builder().category(cat).name("Burger").price(new BigDecimal("35000"))
                .status(ProductStatus.LIVE).inStock(true).build();
        em.persist(product);
        em.persist(ProductVariant.builder().product(product).name("Regular").price(new BigDecimal("35000")).inStock(true).sortOrder(0).build());
        em.persist(ProductVariant.builder().product(product).name("Large").price(new BigDecimal("45000")).inStock(true).sortOrder(1).build());
        em.persist(ProductVariant.builder().product(product).name("Jumbo").price(new BigDecimal("55000")).inStock(false).sortOrder(2).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByProductIdAndInStock — filters by stock")
    void inStockFilter() {
        List<ProductVariant> inStock = variantRepository.findByProductIdAndInStock(product.getId(), true);
        assertEquals(2, inStock.size());
    }

    @Test @DisplayName("findByProductId — returns all variants")
    void allByProduct() {
        List<ProductVariant> all = variantRepository.findByProductId(product.getId());
        assertEquals(3, all.size());
    }

    @Test @DisplayName("existsByProductIdAndName — checks duplicate name")
    void existsByName() {
        assertTrue(variantRepository.existsByProductIdAndName(product.getId(), "Regular"));
        assertFalse(variantRepository.existsByProductIdAndName(product.getId(), "Mini"));
    }
}
