package com.elcafe.modules.sms.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.sms.dto.SmsCampaignRequest;
import com.elcafe.modules.sms.entity.SmsCampaign;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.TargetAudience;
import com.elcafe.modules.sms.repository.SmsCampaignRecipientRepository;
import com.elcafe.modules.sms.repository.SmsCampaignRepository;
import com.elcafe.modules.sms.repository.SmsLogRepository;
import com.elcafe.modules.sms.repository.SmsTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FUNC-8 fail-honestly guarantee for SMS marketing campaigns.
 *
 * <p>Only ALL, BIRTHDAY_TODAY, INACTIVE and NEW_CUSTOMERS have a real recipient query. The other
 * audiences used to lie: SEGMENT/CUSTOM returned an empty list so the campaign reported success
 * while sending to nobody, and LOYAL_CUSTOMERS/HIGH_VALUE fell through the switch default and
 * blasted every active customer. Create and update must now reject those audiences outright.
 */
@ExtendWith(MockitoExtension.class)
class SmsCampaignServiceTest {

    @Mock private SmsCampaignRepository campaignRepository;
    @Mock private SmsCampaignRecipientRepository recipientRepository;
    @Mock private SmsTemplateRepository templateRepository;
    @Mock private SmsLogRepository logRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SmsService smsService;

    @InjectMocks private SmsCampaignService service;

    @ParameterizedTest
    @EnumSource(value = TargetAudience.class, names = {"SEGMENT", "CUSTOM", "LOYAL_CUSTOMERS", "HIGH_VALUE"})
    void createRejectsUnsupportedAudienceWithoutPersistingAnything(TargetAudience audience) {
        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Promo")
                .targetAudience(audience)
                .build();

        assertThatThrownBy(() -> service.createCampaign(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not supported");

        // The campaign never came into existence: no recipient query, no rows written.
        verifyNoInteractions(campaignRepository, recipientRepository, customerRepository);
    }

    @ParameterizedTest
    @EnumSource(value = TargetAudience.class, names = {"SEGMENT", "CUSTOM", "LOYAL_CUSTOMERS", "HIGH_VALUE"})
    void updateRejectsUnsupportedAudienceWithoutSaving(TargetAudience audience) {
        SmsCampaign draft = SmsCampaign.builder().status(CampaignStatus.DRAFT).build();
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(draft));

        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Promo")
                .targetAudience(audience)
                .build();

        assertThatThrownBy(() -> service.updateCampaign(7L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not supported");

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void createWithSupportedAudienceBuildsTheRealRecipientList() {
        when(campaignRepository.save(any(SmsCampaign.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.findByActiveTrue()).thenReturn(List.of());

        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Promo")
                .targetAudience(TargetAudience.ALL)
                .build();

        assertThatCode(() -> service.createCampaign(request)).doesNotThrowAnyException();
        verify(customerRepository).findByActiveTrue();
    }
}
