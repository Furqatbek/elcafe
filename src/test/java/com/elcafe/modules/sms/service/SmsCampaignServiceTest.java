package com.elcafe.modules.sms.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.dto.CustomerActivityDTO;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.customer.service.CustomerActivityService;
import com.elcafe.modules.sms.dto.SmsCampaignRequest;
import com.elcafe.modules.sms.entity.SmsCampaign;
import com.elcafe.modules.sms.entity.SmsCampaignRecipient;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FUNC-8 fail-honestly guarantee for SMS marketing campaigns, plus SEGMENT targeting.
 *
 * <p>ALL, BIRTHDAY_TODAY, INACTIVE, NEW_CUSTOMERS, SEGMENT and CUSTOM have real recipient lists.
 * SEGMENT resolves off filterCriteria — a customer tag or a computed RFM bucket — and must reject
 * campaigns with no/both/unknown criteria. CUSTOM is an explicit phone list and must reject an empty
 * or malformed list, so neither can silently target nobody. LOYAL_CUSTOMERS / HIGH_VALUE remain
 * unimplemented and are rejected outright.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmsCampaignServiceTest {

    @Mock private SmsCampaignRepository campaignRepository;
    @Mock private SmsCampaignRecipientRepository recipientRepository;
    @Mock private SmsTemplateRepository templateRepository;
    @Mock private SmsLogRepository logRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerActivityService customerActivityService;
    @Mock private SmsService smsService;

    @InjectMocks private SmsCampaignService service;

    private void savePassesThrough() {
        when(campaignRepository.save(any(SmsCampaign.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest
    @EnumSource(value = TargetAudience.class, names = {"LOYAL_CUSTOMERS", "HIGH_VALUE"})
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
    @EnumSource(value = TargetAudience.class, names = {"LOYAL_CUSTOMERS", "HIGH_VALUE"})
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
        savePassesThrough();
        when(customerRepository.findByActiveTrue()).thenReturn(List.of());

        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Promo")
                .targetAudience(TargetAudience.ALL)
                .build();

        assertThatCode(() -> service.createCampaign(request)).doesNotThrowAnyException();
        verify(customerRepository).findByActiveTrue();
    }

    // ---------- SEGMENT: tag-based ----------

    @Test
    void createSegmentByTagBuildsRecipientsFromNormalisedTagQuery() {
        savePassesThrough();
        when(customerRepository.findActiveByTagToken("vip")).thenReturn(List.of());

        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("VIP blast")
                .targetAudience(TargetAudience.SEGMENT)
                .filterCriteria(Map.of("tag", "  VIP  ")) // trimmed + lower-cased + space-stripped
                .build();

        assertThatCode(() -> service.createCampaign(request)).doesNotThrowAnyException();
        verify(customerRepository).findActiveByTagToken("vip");
        verifyNoInteractions(customerActivityService);
    }

    // ---------- SEGMENT: RFM-based ----------

    @Test
    void createSegmentByRfmTargetsOnlyMatchingBucket() {
        savePassesThrough();
        CustomerActivityDTO champion = CustomerActivityDTO.builder()
                .customerId(5L).rfmSegment("Champions").build();
        CustomerActivityDTO lost = CustomerActivityDTO.builder()
                .customerId(6L).rfmSegment("Lost").build();
        when(customerActivityService.getAllCustomersActivity()).thenReturn(List.of(champion, lost));
        when(customerRepository.findAllById(any())).thenReturn(List.of(new Customer()));

        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Reward the best")
                .targetAudience(TargetAudience.SEGMENT)
                .filterCriteria(Map.of("rfm_segment", "champions")) // case-insensitive match
                .build();

        assertThatCode(() -> service.createCampaign(request)).doesNotThrowAnyException();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Long>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(customerRepository).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactly(5L); // only the Champion, not the Lost customer
    }

    // ---------- SEGMENT: fail-honestly validation ----------

    @Test
    void createSegmentWithNoCriterionIsRejected() {
        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Nowhere")
                .targetAudience(TargetAudience.SEGMENT)
                .build();

        assertThatThrownBy(() -> service.createCampaign(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exactly one");

        verifyNoInteractions(campaignRepository, recipientRepository, customerRepository);
    }

    @Test
    void createSegmentWithBothCriteriaIsRejected() {
        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Ambiguous")
                .targetAudience(TargetAudience.SEGMENT)
                .filterCriteria(Map.of("tag", "vip", "rfm_segment", "Champions"))
                .build();

        assertThatThrownBy(() -> service.createCampaign(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exactly one");

        verifyNoInteractions(campaignRepository, recipientRepository, customerRepository);
    }

    @Test
    void createSegmentWithUnknownRfmBucketIsRejected() {
        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Typo")
                .targetAudience(TargetAudience.SEGMENT)
                .filterCriteria(Map.of("rfm_segment", "Whales"))
                .build();

        assertThatThrownBy(() -> service.createCampaign(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown RFM segment");

        verifyNoInteractions(campaignRepository, recipientRepository, customerRepository);
    }

    // ---------- CUSTOM: explicit phone list ----------

    @Test
    void createCustomBuildsRecipientsFromPhoneListEnrichingKnownCustomers() {
        savePassesThrough();
        Customer known = new Customer();
        known.setId(42L);
        known.setFirstName("Jamshid");
        known.setLastName("K");
        when(customerRepository.findFirstByPhoneOrderByIdAsc("+998901112233"))
                .thenReturn(Optional.of(known));
        when(customerRepository.findFirstByPhoneOrderByIdAsc("+998904445566"))
                .thenReturn(Optional.empty());

        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Manual blast")
                .targetAudience(TargetAudience.CUSTOM)
                // duplicate + padded entries; the service trims, dedupes and preserves order.
                .filterCriteria(Map.of("phones",
                        List.of("+998901112233", "+998901112233", "  +998904445566  ")))
                .build();

        assertThatCode(() -> service.createCampaign(request)).doesNotThrowAnyException();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SmsCampaignRecipient>> saved = ArgumentCaptor.forClass(List.class);
        verify(recipientRepository).saveAll(saved.capture());
        List<SmsCampaignRecipient> records = saved.getValue();

        assertThat(records).hasSize(2); // duplicate collapsed
        assertThat(records).extracting(SmsCampaignRecipient::getPhone)
                .containsExactly("+998901112233", "+998904445566");
        assertThat(records.get(0).getCustomerId()).isEqualTo(42L);       // matched -> enriched
        assertThat(records.get(0).getCustomerName()).isEqualTo("Jamshid K");
        assertThat(records.get(1).getCustomerId()).isNull();             // unknown number -> bare
        assertThat(records.get(1).getCustomerName()).isNull();
    }

    @Test
    void createCustomWithNoUsablePhonesIsRejected() {
        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Empty")
                .targetAudience(TargetAudience.CUSTOM)
                .filterCriteria(Map.of("phones", List.of("   "))) // blank-only -> nothing usable
                .build();

        assertThatThrownBy(() -> service.createCampaign(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one phone number");

        verifyNoInteractions(campaignRepository, recipientRepository, customerRepository);
    }

    @Test
    void createCustomWithMalformedPhoneIsRejected() {
        SmsCampaignRequest request = SmsCampaignRequest.builder()
                .name("Typo")
                .targetAudience(TargetAudience.CUSTOM)
                .filterCriteria(Map.of("phones", List.of("+998901112233", "not-a-phone")))
                .build();

        assertThatThrownBy(() -> service.createCampaign(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid phone number");

        verifyNoInteractions(campaignRepository, recipientRepository, customerRepository);
    }
}
