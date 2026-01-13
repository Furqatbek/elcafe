package com.elcafe.modules.sms.repository;

import com.elcafe.modules.sms.entity.SmsTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, Long> {

    List<SmsTemplate> findByIsActiveTrue();

    List<SmsTemplate> findByType(String type);

    List<SmsTemplate> findByTypeAndIsActiveTrue(String type);

    Optional<SmsTemplate> findByName(String name);

    Page<SmsTemplate> findByIsActive(Boolean isActive, Pageable pageable);

    @Query("SELECT t FROM SmsTemplate t WHERE t.isActive = true ORDER BY t.usageCount DESC")
    List<SmsTemplate> findMostUsedTemplates(Pageable pageable);

    @Query("SELECT t FROM SmsTemplate t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.content) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<SmsTemplate> searchTemplates(String search, Pageable pageable);

    @Query("SELECT DISTINCT t.type FROM SmsTemplate t")
    List<String> findAllTypes();

    boolean existsByName(String name);
}
