package com.elcafe.modules.selfservice.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.enums.QRCodeType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class QRCodeRepositoryTest {

    @Autowired private QRCodeRepository qrCodeRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().name("Test Cafe").address("123 Main St").build();
        em.persist(restaurant);
    }

    @Test @DisplayName("findUnassignedByRestaurant — returns active QR codes with no table assigned")
    void findUnassignedByRestaurant() {
        em.persist(QRCode.builder().restaurant(restaurant).code("QR-001")
                .qrType(QRCodeType.TAKEAWAY).isActive(true).build());
        em.persist(QRCode.builder().restaurant(restaurant).code("QR-002")
                .qrType(QRCodeType.TABLE).isActive(true).build());
        em.persist(QRCode.builder().restaurant(restaurant).code("QR-003")
                .qrType(QRCodeType.TABLE).isActive(false).build());

        em.flush(); em.clear();

        List<QRCode> unassigned = qrCodeRepository.findUnassignedByRestaurant(restaurant.getId());

        assertEquals(2, unassigned.size());
        assertTrue(unassigned.stream().allMatch(q -> q.getTable() == null));
        assertTrue(unassigned.stream().allMatch(q -> Boolean.TRUE.equals(q.getIsActive())));
    }

    @Test @DisplayName("countActiveByRestaurant — counts only active QR codes")
    void countActiveByRestaurant() {
        em.persist(QRCode.builder().restaurant(restaurant).code("QR-010")
                .qrType(QRCodeType.TABLE).isActive(true).build());
        em.persist(QRCode.builder().restaurant(restaurant).code("QR-011")
                .qrType(QRCodeType.TABLE).isActive(true).build());
        em.persist(QRCode.builder().restaurant(restaurant).code("QR-012")
                .qrType(QRCodeType.TABLE).isActive(false).build());

        em.flush(); em.clear();

        assertEquals(2L, qrCodeRepository.countActiveByRestaurant(restaurant.getId()));
    }
}
