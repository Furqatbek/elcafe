package com.elcafe.modules.sms.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.sms.entity.SmsTemplate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class SmsTemplateRepositoryTest {

    @Autowired private SmsTemplateRepository repo;
    @Autowired private EntityManager em;

    @BeforeEach
    void setUp() {
        em.persist(SmsTemplate.builder()
                .name("Welcome SMS")
                .content("Welcome to ElCafe, {name}!")
                .type("WELCOME")
                .description("Sent on registration")
                .isActive(true)
                .usageCount(50)
                .build());
        em.persist(SmsTemplate.builder()
                .name("Order Confirmation")
                .content("Your order #{orderId} has been confirmed.")
                .type("ORDER")
                .description("Sent after order placed")
                .isActive(true)
                .usageCount(120)
                .build());
        em.persist(SmsTemplate.builder()
                .name("Promo Inactive")
                .content("Special promo for you!")
                .type("PROMO")
                .description("Disabled promo template")
                .isActive(false)
                .usageCount(5)
                .build());
        em.flush();
        em.clear();
    }

    @Test @DisplayName("findMostUsedTemplates — returns active templates ordered by usageCount DESC")
    void findMostUsedTemplates() {
        List<SmsTemplate> result = repo.findMostUsedTemplates(PageRequest.of(0, 10));

        assertEquals(2, result.size());
        assertEquals("Order Confirmation", result.get(0).getName());
        assertEquals("Welcome SMS", result.get(1).getName());
        // inactive template should not appear
        assertTrue(result.stream().noneMatch(t -> "Promo Inactive".equals(t.getName())));
    }

    @Test @DisplayName("findMostUsedTemplates — respects page size limit")
    void findMostUsedTemplates_limit() {
        List<SmsTemplate> result = repo.findMostUsedTemplates(PageRequest.of(0, 1));
        assertEquals(1, result.size());
        assertEquals("Order Confirmation", result.get(0).getName());
    }

    @Test @DisplayName("searchTemplates — matches by name")
    void searchTemplates_byName() {
        Page<SmsTemplate> result = repo.searchTemplates("Welcome", PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertEquals("Welcome SMS", result.getContent().get(0).getName());
    }

    @Test @DisplayName("searchTemplates — matches by content")
    void searchTemplates_byContent() {
        Page<SmsTemplate> result = repo.searchTemplates("confirmed", PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertEquals("Order Confirmation", result.getContent().get(0).getName());
    }

    @Test @DisplayName("searchTemplates — case insensitive and includes inactive")
    void searchTemplates_caseInsensitive() {
        Page<SmsTemplate> result = repo.searchTemplates("promo", PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertEquals("Promo Inactive", result.getContent().get(0).getName());
    }

    @Test @DisplayName("searchTemplates — no match returns empty")
    void searchTemplates_noMatch() {
        Page<SmsTemplate> result = repo.searchTemplates("nonexistent", PageRequest.of(0, 10));
        assertEquals(0, result.getTotalElements());
    }

    @Test @DisplayName("findAllTypes — returns distinct types")
    void findAllTypes() {
        List<String> types = repo.findAllTypes();
        assertEquals(3, types.size());
        assertTrue(types.contains("WELCOME"));
        assertTrue(types.contains("ORDER"));
        assertTrue(types.contains("PROMO"));
    }

    @Test @DisplayName("findByIsActiveTrue — returns only active templates")
    void findByIsActiveTrue() {
        List<SmsTemplate> active = repo.findByIsActiveTrue();
        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(SmsTemplate::getIsActive));
    }

    @Test @DisplayName("findByType — returns templates of given type")
    void findByType() {
        List<SmsTemplate> orderTemplates = repo.findByType("ORDER");
        assertEquals(1, orderTemplates.size());
        assertEquals("Order Confirmation", orderTemplates.get(0).getName());

        List<SmsTemplate> noMatch = repo.findByType("NONEXISTENT");
        assertTrue(noMatch.isEmpty());
    }
}
