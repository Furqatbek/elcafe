package com.elcafe.modules.sms.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.sms.dto.SmsAutomationRuleRequest;
import com.elcafe.modules.sms.entity.SmsAutomationRule;
import com.elcafe.modules.sms.entity.SmsTemplate;
import com.elcafe.modules.sms.enums.AutomationTrigger;
import com.elcafe.modules.sms.repository.SmsAutomationRuleRepository;
import com.elcafe.modules.sms.repository.SmsLogRepository;
import com.elcafe.modules.sms.repository.SmsTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FUNC-8 fail-honestly guarantee for SMS automation rules: delayed delivery is not implemented
 * ({@code triggerAutomation} always sends immediately), so a non-zero delayMinutes must be rejected
 * at write time instead of being stored and then silently ignored.
 */
@ExtendWith(MockitoExtension.class)
class SmsAutomationServiceTest {

    @Mock private SmsAutomationRuleRepository ruleRepository;
    @Mock private SmsTemplateRepository templateRepository;
    @Mock private SmsLogRepository logRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SmsService smsService;

    @InjectMocks private SmsAutomationService service;

    @Test
    void createRejectsNonZeroDelayWithoutPersistingAnything() {
        SmsAutomationRuleRequest request = SmsAutomationRuleRequest.builder()
                .name("Welcome")
                .triggerType(AutomationTrigger.WELCOME)
                .templateId(1L)
                .delayMinutes(60)
                .build();

        assertThatThrownBy(() -> service.createRule(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Delayed sending is not supported");

        verifyNoInteractions(ruleRepository, templateRepository);
    }

    @Test
    void updateRejectsNonZeroDelayWithoutSaving() {
        SmsAutomationRule existing = SmsAutomationRule.builder().name("Welcome").build();
        when(ruleRepository.findById(3L)).thenReturn(Optional.of(existing));

        SmsAutomationRuleRequest request = SmsAutomationRuleRequest.builder()
                .name("Welcome")
                .triggerType(AutomationTrigger.WELCOME)
                .templateId(1L)
                .delayMinutes(60)
                .build();

        assertThatThrownBy(() -> service.updateRule(3L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Delayed sending is not supported");

        verify(ruleRepository, never()).save(any());
    }

    @Test
    void createAcceptsImmediateRule() {
        when(ruleRepository.existsByName("Welcome")).thenReturn(false);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(new SmsTemplate()));
        when(ruleRepository.save(any(SmsAutomationRule.class))).thenAnswer(inv -> inv.getArgument(0));

        SmsAutomationRuleRequest request = SmsAutomationRuleRequest.builder()
                .name("Welcome")
                .triggerType(AutomationTrigger.WELCOME)
                .templateId(1L)
                .delayMinutes(0)
                .build();

        assertThatCode(() -> service.createRule(request)).doesNotThrowAnyException();
        verify(ruleRepository).save(any(SmsAutomationRule.class));
    }
}
