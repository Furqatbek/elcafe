package com.elcafe.modules.sms.repository;

import com.elcafe.modules.sms.entity.SmsAutomationRule;
import com.elcafe.modules.sms.enums.AutomationTrigger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmsAutomationRuleRepository extends JpaRepository<SmsAutomationRule, Long> {

    List<SmsAutomationRule> findByIsActiveTrue();

    List<SmsAutomationRule> findByTriggerType(AutomationTrigger triggerType);

    List<SmsAutomationRule> findByTriggerTypeAndIsActiveTrue(AutomationTrigger triggerType);

    Optional<SmsAutomationRule> findByName(String name);

    Page<SmsAutomationRule> findByIsActive(Boolean isActive, Pageable pageable);

    @Query("SELECT r FROM SmsAutomationRule r LEFT JOIN FETCH r.template WHERE r.triggerType = :triggerType AND r.isActive = true")
    List<SmsAutomationRule> findActiveRulesWithTemplate(@Param("triggerType") AutomationTrigger triggerType);

    @Query("SELECT r FROM SmsAutomationRule r LEFT JOIN FETCH r.template WHERE r.id = :id")
    Optional<SmsAutomationRule> findByIdWithTemplate(@Param("id") Long id);

    @Query("SELECT SUM(r.sentCount) FROM SmsAutomationRule r WHERE r.isActive = true")
    Long getTotalSentByAutomation();

    @Query("SELECT r.triggerType, COUNT(r) FROM SmsAutomationRule r WHERE r.isActive = true GROUP BY r.triggerType")
    List<Object[]> countActiveByTriggerType();

    boolean existsByName(String name);

    @Query("SELECT r FROM SmsAutomationRule r ORDER BY r.sentCount DESC")
    List<SmsAutomationRule> findMostUsedRules(Pageable pageable);
}
