package uz.megahotdog.modules.telegram.repository;

import uz.megahotdog.modules.telegram.entity.TelegramAutomationRule;
import uz.megahotdog.modules.telegram.enums.TelegramTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramAutomationRuleRepository extends JpaRepository<TelegramAutomationRule, Long> {

    List<TelegramAutomationRule> findByIsActiveTrue();

    List<TelegramAutomationRule> findByTriggerTypeAndIsActiveTrue(TelegramTriggerType triggerType);

    boolean existsByName(String name);

    @Query("SELECT r FROM TelegramAutomationRule r LEFT JOIN FETCH r.template WHERE r.id = :id")
    Optional<TelegramAutomationRule> findByIdWithTemplate(@Param("id") Long id);

    @Query("SELECT r FROM TelegramAutomationRule r LEFT JOIN FETCH r.template WHERE r.triggerType = :triggerType AND r.isActive = true")
    List<TelegramAutomationRule> findActiveRulesWithTemplate(@Param("triggerType") TelegramTriggerType triggerType);
}
