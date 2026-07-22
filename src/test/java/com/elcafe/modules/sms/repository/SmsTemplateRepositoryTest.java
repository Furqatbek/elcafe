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
                .name("Welcome SMS").content("Welcome to our service, {name}!")
                .type("MARKETING").isActive(true).usageCount(50).build());
        em.persist(SmsTemplate.builder()
                .name("OTP Code").content("Your verification code is {code}")
                .type("AUTH").isActive(true).usageCount(200).build());
        em.persist(SmsTemplate.builder()
                .name("Old Promo").content("Big sale this weekend!")
                .type("MARKETING").isActive(false).usageCount(10).build());
        em.flush();
        em.clear();
    }

    @Test @DisplayName("findMostUsedTemplates — returns active templates ordered by usageCount DESC")
    void findMostUsedTemplates() {
        List<SmsTemplate> result = repo.findMostUsedTemplates(PageRequest.of(0, 10));

        assertEquals(2, result.size());
        assertEquals("OTP Code", result.get(0).getName());
        assertEquals("Welcome SMS", result.get(1).getName());
    }

    @Test @DisplayName("findMostUsedTemplates — respects page size limit")
    void findMostUsedTemplates_pageable() {
        List<SmsTemplate> result = repo.findMostUsedTemplates(PageRequest.of(0, 1));

        assertEquals(1, result.size());
        assertEquals("OTP Code", result.get(0).getName());
    }

    @Test @DisplayName("searchTemplates — matches name case-insensitively")
    void searchTemplates_byName() {
        Page<SmsTemplate> result = repo.searchTemplates("welcome", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("Welcome SMS", result.getContent().get(0).getName());
    }

    @Test @DisplayName("searchTemplates — matches content")
    void searchTemplates_byContent() {
        Page<SmsTemplate> result = repo.searchTemplates("verification", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("OTP Code", result.getContent().get(0).getName());
    }

    @Test @DisplayName("searchTemplates — returns inactive templates too")
    void searchTemplates_includesInactive() {
        Page<SmsTemplate> result = repo.searchTemplates("sale", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("Old Promo", result.getContent().get(0).getName());
    }

    @Test @DisplayName("findAllTypes — returns distinct types")
    void findAllTypes() {
        List<String> types = repo.findAllTypes();

        assertEquals(2, types.size());
        assertTrue(types.contains("MARKETING"));
        assertTrue(types.contains("AUTH"));
    }

    @Test @DisplayName("findByIsActiveTrue — returns only active templates")
    void findByIsActiveTrue() {
        List<SmsTemplate> result = repo.findByIsActiveTrue();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(SmsTemplate::getIsActive));
    }

    @Test @DisplayName("findByType — filters by type")
    void findByType() {
        List<SmsTemplate> marketing = repo.findByType("MARKETING");
        assertEquals(2, marketing.size());

        List<SmsTemplate> auth = repo.findByType("AUTH");
        assertEquals(1, auth.size());
        assertEquals("OTP Code", auth.get(0).getName());

        List<SmsTemplate> unknown = repo.findByType("UNKNOWN");
        assertTrue(unknown.isEmpty());
    }
}
