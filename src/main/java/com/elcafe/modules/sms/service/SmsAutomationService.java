package com.elcafe.modules.sms.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.sms.dto.*;
import com.elcafe.modules.sms.entity.*;
import com.elcafe.modules.sms.enums.*;
import com.elcafe.modules.sms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsAutomationService {

    private final SmsAutomationRuleRepository ruleRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final SmsTemplateRepository templateRepository;
    private final SmsLogRepository logRepository;
    private final CustomerRepository customerRepository;
    private final SmsService smsService;

    @Transactional(readOnly = true)
    public Page<SmsAutomationRuleResponse> getAllRules(Pageable pageable) {
        return ruleRepository.findAll(pageable).map(SmsAutomationRuleResponse::from);
    }

    @Transactional(readOnly = true)
    public List<SmsAutomationRuleResponse> getActiveRules() {
        return ruleRepository.findByIsActiveTrue().stream()
                .map(SmsAutomationRuleResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SmsAutomationRuleResponse getRuleById(Long id) {
        SmsAutomationRule rule = ruleRepository.findByIdWithTemplate(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsAutomationRule", "id", id));
        return SmsAutomationRuleResponse.from(rule);
    }

    @Transactional
    public SmsAutomationRuleResponse createRule(SmsAutomationRuleRequest request) {
        log.info("Creating SMS automation rule: {}", request.getName());
        requireImmediateDelivery(request.getDelayMinutes());

        if (ruleRepository.existsByName(request.getName())) {
            throw new BadRequestException("Rule with name '" + request.getName() + "' already exists");
        }

        SmsTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", request.getTemplateId()));

        SmsAutomationRule rule = SmsAutomationRule.builder()
                .restaurantId(requireWritableTenant())
                .name(request.getName())
                .description(request.getDescription())
                .triggerType(request.getTriggerType())
                .template(template)
                .delayMinutes(request.getDelayMinutes() != null ? request.getDelayMinutes() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .conditions(request.getConditions())
                .build();

        rule = ruleRepository.save(rule);
        log.info("SMS automation rule created with ID: {}", rule.getId());
        return SmsAutomationRuleResponse.from(rule);
    }

    @Transactional
    public SmsAutomationRuleResponse updateRule(Long id, SmsAutomationRuleRequest request) {
        log.info("Updating SMS automation rule: {}", id);

        SmsAutomationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsAutomationRule", "id", id));

        // Check name uniqueness if changed
        if (!rule.getName().equals(request.getName()) && ruleRepository.existsByName(request.getName())) {
            throw new BadRequestException("Rule with name '" + request.getName() + "' already exists");
        }
        requireImmediateDelivery(request.getDelayMinutes());

        SmsTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("SmsTemplate", "id", request.getTemplateId()));

        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setTriggerType(request.getTriggerType());
        rule.setTemplate(template);
        rule.setDelayMinutes(request.getDelayMinutes() != null ? request.getDelayMinutes() : 0);
        rule.setConditions(request.getConditions());
        if (request.getIsActive() != null) {
            rule.setIsActive(request.getIsActive());
        }

        rule = ruleRepository.save(rule);
        log.info("SMS automation rule updated: {}", id);
        return SmsAutomationRuleResponse.from(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        log.info("Deleting SMS automation rule: {}", id);
        SmsAutomationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsAutomationRule", "id", id));
        ruleRepository.delete(rule);
        log.info("SMS automation rule deleted: {}", id);
    }

    @Transactional
    public SmsAutomationRuleResponse toggleRuleStatus(Long id) {
        SmsAutomationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SmsAutomationRule", "id", id));
        rule.setIsActive(!Boolean.TRUE.equals(rule.getIsActive()));
        rule = ruleRepository.save(rule);
        log.info("SMS automation rule {} status toggled to: {}", id, rule.getIsActive());
        return SmsAutomationRuleResponse.from(rule);
    }

    /**
     * Trigger automation for a specific event
     */
    @Transactional
    public void triggerAutomation(AutomationTrigger triggerType, Customer customer, Map<String, Object> context) {
        log.info("Triggering automation {} for customer {}", triggerType, customer.getId());

        List<SmsAutomationRule> rules = ruleRepository.findActiveRulesWithTemplate(triggerType);

        for (SmsAutomationRule rule : rules) {
            try {
                if (!rule.shouldTrigger(context)) {
                    log.debug("Rule {} conditions not met for customer {}", rule.getId(), customer.getId());
                    continue;
                }

                // Check if we've already sent this message recently (prevent duplicates)
                String message = renderMessage(rule.getTemplate(), customer, context);
                LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
                if (logRepository.existsByCustomerIdAndMessageAndCreatedAtAfter(customer.getId(), message, oneDayAgo)) {
                    log.debug("Duplicate message detected, skipping for customer {}", customer.getId());
                    continue;
                }

                // Delayed delivery is rejected at create/update time (requireImmediateDelivery),
                // so every active rule sends immediately.
                sendAutomatedSms(rule, customer, message);

                rule.incrementSentCount();
                ruleRepository.save(rule);

            } catch (Exception e) {
                log.error("Failed to process automation rule {} for customer {}: {}",
                        rule.getId(), customer.getId(), e.getMessage());
            }
        }
    }

    /**
     * Trigger welcome SMS for new customer
     */
    @Transactional
    public void triggerWelcomeSms(Customer customer) {
        triggerAutomation(AutomationTrigger.WELCOME, customer, new HashMap<>());
    }

    /**
     * Trigger birthday SMS
     */
    @Transactional
    public void triggerBirthdaySms(Customer customer) {
        Map<String, Object> context = new HashMap<>();
        context.put("year", String.valueOf(java.time.LocalDate.now().getYear()));
        triggerAutomation(AutomationTrigger.BIRTHDAY, customer, context);
    }

    /**
     * Trigger referral reward SMS
     */
    @Transactional
    public void triggerReferralRewardSms(Customer customer, String amount) {
        Map<String, Object> context = new HashMap<>();
        context.put("amount", amount);
        triggerAutomation(AutomationTrigger.REFERRAL_REWARD, customer, context);
    }

    // ========== Helper Methods ==========

    /**
     * Delayed automation delivery is not implemented — {@link #triggerAutomation} sends immediately
     * regardless of the configured delay. Reject a non-zero delay at create/update time (FUNC-8:
     * fail honestly) instead of storing a value the engine silently ignores.
     */
    private void requireImmediateDelivery(Integer delayMinutes) {
        if (delayMinutes != null && delayMinutes > 0) {
            throw new BadRequestException(
                    "Delayed sending is not supported yet — set delayMinutes to 0 (or leave it empty) "
                            + "for immediate delivery.");
        }
    }

    private void sendAutomatedSms(SmsAutomationRule rule, Customer customer, String message) {
        try {
            SendSmsResponse response = smsService.sendSms(
                    SendSmsRequest.builder()
                            .mobilePhone(customer.getPhone())
                            .message(message)
                            .build()
            );

            // Log the message
            SmsLog log = SmsLog.builder()
                    .restaurantId(rule.getRestaurantId())
                    .customerId(customer.getId())
                    .phone(customer.getPhone())
                    .customerName(customer.getFirstName() + " " + customer.getLastName())
                    .message(message)
                    .messageType(SmsMessageType.AUTOMATION)
                    .template(rule.getTemplate())
                    .automationRule(rule)
                    .status(response != null && response.getData() != null ? MessageStatus.SENT : MessageStatus.FAILED)
                    .eskizMessageId(response != null && response.getData() != null ? response.getData().getId() : null)
                    .sentAt(LocalDateTime.now())
                    .build();

            logRepository.save(log);

            this.log.info("Automated SMS sent to customer {} via rule {}", customer.getId(), rule.getName());

        } catch (Exception e) {
            log.error("Failed to send automated SMS to customer {}: {}", customer.getId(), e.getMessage());

            // Log the failure
            SmsLog logEntry = SmsLog.builder()
                    .restaurantId(rule.getRestaurantId())
                    .customerId(customer.getId())
                    .phone(customer.getPhone())
                    .customerName(customer.getFirstName() + " " + customer.getLastName())
                    .message(message)
                    .messageType(SmsMessageType.AUTOMATION)
                    .template(rule.getTemplate())
                    .automationRule(rule)
                    .status(MessageStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();

            logRepository.save(logEntry);
        }
    }

    private String renderMessage(SmsTemplate template, Customer customer, Map<String, Object> context) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", customer.getFirstName());
        placeholders.put("full_name", customer.getFirstName() + " " + customer.getLastName());
        placeholders.put("phone", customer.getPhone());

        // Add context values
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            placeholders.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        return template.render(placeholders);
    }

    /**
     * V165: SMS marketing data belongs to a restaurant — a campaign may only ever target its own
     * customers. A platform account has no customer base of its own to message.
     */
    private Long requireWritableTenant() {
        Long restaurantId = restaurantAuthorizationService.currentTenantScopeStrict();
        if (restaurantId == null) {
            throw new BadRequestException(
                    "An SMS automation rule belongs to a restaurant. Sign in with a restaurant-scoped account "
                            + "to create one.");
        }
        return restaurantId;
    }
}
