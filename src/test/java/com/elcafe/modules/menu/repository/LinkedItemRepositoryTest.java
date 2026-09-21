package com.elcafe.modules.menu.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.LinkedItem;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.LinkType;
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
class LinkedItemRepositoryTest {

    @Autowired private LinkedItemRepository linkedItemRepository;
    @Autowired private EntityManager em;

    private Product steak;
    private Product wine;
    private Product fries;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Test"); restaurant.setAddress("123 St"); restaurant.setActive(true);
        em.persist(restaurant);
        Category cat = Category.builder().restaurant(restaurant).name("Main").sortOrder(0).active(true).build();
        em.persist(cat);
        steak = Product.builder().category(cat).name("Steak").price(new BigDecimal("80000")).status(ProductStatus.LIVE).inStock(true).build();
        wine = Product.builder().category(cat).name("Wine").price(new BigDecimal("50000")).status(ProductStatus.LIVE).inStock(true).build();
        fries = Product.builder().category(cat).name("Fries").price(new BigDecimal("15000")).status(ProductStatus.LIVE).inStock(true).build();
        em.persist(steak); em.persist(wine); em.persist(fries);
        em.persist(LinkedItem.builder().product(steak).linkedProduct(wine).linkType(LinkType.RECOMMENDED).sortOrder(0).build());
        em.persist(LinkedItem.builder().product(steak).linkedProduct(fries).linkType(LinkType.COMPLEMENTARY).sortOrder(1).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByProductIdAndLinkTypeOrderBySortOrder — filters by type")
    void byType() {
        List<LinkedItem> recommended = linkedItemRepository.findByProductIdAndLinkTypeOrderBySortOrder(steak.getId(), LinkType.RECOMMENDED);
        assertEquals(1, recommended.size());
        assertEquals("Wine", recommended.get(0).getLinkedProduct().getName());
    }

    @Test @DisplayName("existsByProductIdAndLinkedProductId — checks duplicates")
    void existsCheck() {
        assertTrue(linkedItemRepository.existsByProductIdAndLinkedProductId(steak.getId(), wine.getId()));
        assertFalse(linkedItemRepository.existsByProductIdAndLinkedProductId(wine.getId(), steak.getId()));
    }

    @Test @DisplayName("findByProductIdOrderBySortOrder — returns sorted linked items")
    void sortedByOrder() {
        List<LinkedItem> items = linkedItemRepository.findByProductIdOrderBySortOrder(steak.getId());
        assertEquals(2, items.size());
        assertEquals(LinkType.RECOMMENDED, items.get(0).getLinkType());
        assertEquals(LinkType.COMPLEMENTARY, items.get(1).getLinkType());
    }
}
