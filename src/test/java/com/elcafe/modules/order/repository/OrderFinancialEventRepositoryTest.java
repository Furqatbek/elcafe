package com.elcafe.modules.order.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.order.entity.OrderFinancialEvent;
import com.elcafe.modules.order.entity.OrderFinancialEvent.EventType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class OrderFinancialEventRepositoryTest {

    @Autowired private OrderFinancialEventRepository eventRepository;
    @Autowired private EntityManager em;

    private static final Long ORDER_ID = 999L;

    @BeforeEach
    void setUp() {
        em.persist(OrderFinancialEvent.builder()
                .orderId(ORDER_ID).orderNumber("ORD-001")
                .sequenceNumber(1).eventType(EventType.ORDER_CREATED)
                .build());
        em.persist(OrderFinancialEvent.builder()
                .orderId(ORDER_ID).orderNumber("ORD-001")
                .sequenceNumber(2).eventType(EventType.ITEM_ADDED)
                .build());
        em.persist(OrderFinancialEvent.builder()
                .orderId(ORDER_ID).orderNumber("ORD-001")
                .sequenceNumber(3).eventType(EventType.PAYMENT_RECEIVED)
                .build());
    }

    @Test
    @DisplayName("getMaxSequenceNumber returns highest sequence number for an order")
    void getMaxSequenceNumber() {
        em.flush();
        em.clear();

        Optional<Integer> max = eventRepository.getMaxSequenceNumber(ORDER_ID);
        assertTrue(max.isPresent());
        assertEquals(3, max.get());
    }

    @Test
    @DisplayName("getMaxSequenceNumber returns empty for non-existent order")
    void getMaxSequenceNumber_noEvents() {
        em.flush();
        em.clear();

        Optional<Integer> max = eventRepository.getMaxSequenceNumber(12345L);
        assertTrue(max.isEmpty());
    }

    @Test
    @DisplayName("getLatestEvent returns the event with highest sequence number")
    void getLatestEvent() {
        em.flush();
        em.clear();

        Optional<OrderFinancialEvent> latest = eventRepository.getLatestEvent(ORDER_ID);
        assertTrue(latest.isPresent());
        assertEquals(3, latest.get().getSequenceNumber());
        assertEquals(EventType.PAYMENT_RECEIVED, latest.get().getEventType());
    }
}
