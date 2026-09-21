package com.elcafe.modules.selfservice.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.selfservice.entity.SelfServiceCartItem;
import com.elcafe.modules.selfservice.entity.SelfServiceSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class SelfServiceCartItemRepositoryTest {

    @Autowired private SelfServiceCartItemRepository repo;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private SelfServiceSession session;
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder()
                .name("Test Cafe")
                .address("123 Main St")
                .active(true)
                .build();
        em.persist(restaurant);

        RestaurantTable table = RestaurantTable.builder()
                .restaurant(restaurant)
                .tableNumber("T1")
                .tableName("Table One")
                .status(RestaurantTable.TableStatus.AVAILABLE)
                .capacity(4)
                .active(true)
                .build();
        em.persist(table);

        session = SelfServiceSession.builder()
                .sessionToken("cart-session-token")
                .restaurant(restaurant)
                .table(table)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        em.persist(session);

        Category category = Category.builder()
                .restaurant(restaurant)
                .name("Beverages")
                .active(true)
                .sortOrder(0)
                .build();
        em.persist(category);

        product1 = Product.builder()
                .category(category)
                .name("Coffee")
                .price(new BigDecimal("5.00"))
                .inStock(true)
                .build();
        em.persist(product1);

        product2 = Product.builder()
                .category(category)
                .name("Tea")
                .price(new BigDecimal("3.00"))
                .inStock(true)
                .build();
        em.persist(product2);
    }

    @Test
    void countItemsInCart_sumsQuantities() {
        em.persist(SelfServiceCartItem.builder()
                .session(session)
                .product(product1)
                .quantity(3)
                .unitPrice(new BigDecimal("5.00"))
                .modifiers(new ArrayList<>())
                .build());

        em.persist(SelfServiceCartItem.builder()
                .session(session)
                .product(product2)
                .quantity(5)
                .unitPrice(new BigDecimal("3.00"))
                .modifiers(new ArrayList<>())
                .build());

        em.flush(); em.clear();

        Integer count = repo.countItemsInCart(session.getId());

        assertEquals(8, count);
    }

    @Test
    @Transactional
    void deleteAllBySessionId_removesAll() {
        em.persist(SelfServiceCartItem.builder()
                .session(session)
                .product(product1)
                .quantity(1)
                .unitPrice(new BigDecimal("5.00"))
                .modifiers(new ArrayList<>())
                .build());

        em.persist(SelfServiceCartItem.builder()
                .session(session)
                .product(product2)
                .quantity(2)
                .unitPrice(new BigDecimal("3.00"))
                .modifiers(new ArrayList<>())
                .build());

        em.flush(); em.clear();

        repo.deleteAllBySessionId(session.getId());

        em.flush(); em.clear();

        List<SelfServiceCartItem> items = repo.findBySessionIdOrderByAddedAtAsc(session.getId());
        assertTrue(items.isEmpty());
    }

    @Test
    void findBySessionIdAndProductIdAndVariantId_findsExact() {
        em.persist(SelfServiceCartItem.builder()
                .session(session)
                .product(product1)
                .variant(null)
                .quantity(1)
                .unitPrice(new BigDecimal("5.00"))
                .modifiers(new ArrayList<>())
                .build());

        em.persist(SelfServiceCartItem.builder()
                .session(session)
                .product(product2)
                .variant(null)
                .quantity(2)
                .unitPrice(new BigDecimal("3.00"))
                .modifiers(new ArrayList<>())
                .build());

        em.flush(); em.clear();

        Optional<SelfServiceCartItem> found = repo.findBySessionIdAndProductIdAndVariantId(
                session.getId(), product1.getId(), null);

        assertTrue(found.isPresent());
        assertEquals("Coffee", found.get().getProduct().getName());
    }

    @Test
    void findBySessionIdOrderByAddedAtAsc_ordersCorrectly() {
        SelfServiceCartItem item1 = SelfServiceCartItem.builder()
                .session(session)
                .product(product1)
                .quantity(1)
                .unitPrice(new BigDecimal("5.00"))
                .modifiers(new ArrayList<>())
                .build();
        em.persist(item1);

        SelfServiceCartItem item2 = SelfServiceCartItem.builder()
                .session(session)
                .product(product2)
                .quantity(2)
                .unitPrice(new BigDecimal("3.00"))
                .modifiers(new ArrayList<>())
                .build();
        em.persist(item2);

        em.flush();

        em.createNativeQuery("UPDATE self_service_cart_items SET added_at = :time WHERE id = :id")
                .setParameter("time", LocalDateTime.now().minusHours(1))
                .setParameter("id", item1.getId())
                .executeUpdate();

        em.createNativeQuery("UPDATE self_service_cart_items SET added_at = :time WHERE id = :id")
                .setParameter("time", LocalDateTime.now())
                .setParameter("id", item2.getId())
                .executeUpdate();

        em.flush(); em.clear();

        List<SelfServiceCartItem> items = repo.findBySessionIdOrderByAddedAtAsc(session.getId());

        assertEquals(2, items.size());
        assertEquals("Coffee", items.get(0).getProduct().getName());
        assertEquals("Tea", items.get(1).getProduct().getName());
    }
}
