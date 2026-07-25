package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramCampaignRequest;
import com.elcafe.modules.instagram.entity.InstagramCampaign;
import com.elcafe.modules.instagram.entity.InstagramCampaignRecipient;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.repository.InstagramCampaignRecipientRepository;
import com.elcafe.modules.instagram.repository.InstagramCampaignRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.sms.enums.CampaignStatus;
import com.elcafe.modules.sms.enums.MessageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenant isolation and create/enqueue behaviour of the Instagram campaign service — the async
 * replacement for the old synchronous broadcast. A campaign and its recipients are stamped with the
 * caller's own restaurant; a foreign campaign id reads as not-found; a platform account cannot create
 * one; and the recipient set is built only from the caller's own subscribers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramCampaignServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long FOREIGN_ID = 100L;
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock private InstagramCampaignRepository campaignRepository;
    @Mock private InstagramCampaignRecipientRepository recipientRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramCampaignExecutor executor;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    @InjectMocks private InstagramCampaignService service;

    private InstagramSubscriber sub(long id, String igsid) {
        return InstagramSubscriber.builder()
                .id(id).restaurantId(TENANT_B).igsid(igsid).isActive(true).isBlocked(false).build();
    }

    private InstagramCampaignRequest request(String message, String audience) {
        InstagramCampaignRequest r = new InstagramCampaignRequest();
        r.setMessageText(message);
        r.setTargetAudience(audience);
        return r;
    }

    /** Save assigns an id and echoes the entity; recipients are captured for assertions. */
    private void stubSavesAsManagerOfB(AtomicReference<List<InstagramCampaignRecipient>> savedRecipients) {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(campaignRepository.save(any())).thenAnswer(inv -> {
            InstagramCampaign c = inv.getArgument(0);
            if (c.getId() == null) c.setId(10L);
            return c;
        });
        when(recipientRepository.saveAll(any())).thenAnswer(inv -> {
            savedRecipients.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    @Test
    @DisplayName("create stamps the campaign and every recipient with the caller's own restaurant")
    void createStampsCallersRestaurant() {
        AtomicReference<List<InstagramCampaignRecipient>> saved = new AtomicReference<>();
        stubSavesAsManagerOfB(saved);
        when(subscriberRepository.findAllActiveNotBlocked(TENANT_B))
                .thenReturn(List.of(sub(1L, "igsid-1"), sub(2L, "igsid-2")));

        service.createCampaign(request("promo", "ALL"));

        assertThat(saved.get()).hasSize(2)
                .allSatisfy(r -> {
                    assertThat(r.getRestaurantId()).isEqualTo(TENANT_B);
                    assertThat(r.getStatus()).isEqualTo(MessageStatus.PENDING);
                });
        // Recipients came from THIS restaurant's subscribers, never another tenant's.
        verify(subscriberRepository).findAllActiveNotBlocked(TENANT_B);
        verify(subscriberRepository, never()).findAllActiveNotBlocked(TENANT_A);
    }

    @Test
    @DisplayName("a REGISTERED campaign targets only the caller's registered subscribers")
    void registeredAudienceUsesRegisteredQuery() {
        AtomicReference<List<InstagramCampaignRecipient>> saved = new AtomicReference<>();
        stubSavesAsManagerOfB(saved);
        when(subscriberRepository.findAllRegistered(TENANT_B)).thenReturn(List.of(sub(3L, "igsid-3")));

        service.createCampaign(request("promo", "REGISTERED"));

        verify(subscriberRepository).findAllRegistered(TENANT_B);
        verify(subscriberRepository, never()).findAllActiveNotBlocked(any());
    }

    @Test
    @DisplayName("a platform (SUPER_ADMIN) account cannot create a campaign — it must act as a restaurant")
    void superAdminCannotCreate() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(null); // SUPER_ADMIN

        assertThatThrownBy(() -> service.createCampaign(request("promo", "ALL")))
                .isInstanceOf(BadRequestException.class);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("a blank message is rejected before anything is persisted")
    void blankMessageRejected() {
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);

        assertThatThrownBy(() -> service.createCampaign(request("   ", "ALL")))
                .isInstanceOf(BadRequestException.class);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("reading another restaurant's campaign is not-found, never the bare findById")
    void cannotReadForeignCampaign() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(campaignRepository.findByIdAndRestaurantId(FOREIGN_ID, TENANT_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCampaign(FOREIGN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(campaignRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("listing is confined to the caller's own restaurant, never findAll()")
    void listScopedToOwnRestaurant() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(campaignRepository.findByRestaurantIdOrderByIdDesc(TENANT_B, PAGE)).thenReturn(Page.empty());

        service.list(PAGE);

        verify(campaignRepository).findByRestaurantIdOrderByIdDesc(TENANT_B, PAGE);
        verify(campaignRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("createAndSend moves the campaign to SENDING and hands it to the async executor")
    void createAndSendTriggersExecutorAsync() {
        AtomicReference<List<InstagramCampaignRecipient>> saved = new AtomicReference<>();
        AtomicReference<InstagramCampaign> savedCampaign = new AtomicReference<>();
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT_B);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT_B);
        when(campaignRepository.save(any())).thenAnswer(inv -> {
            InstagramCampaign c = inv.getArgument(0);
            if (c.getId() == null) c.setId(10L);
            savedCampaign.set(c);
            return c;
        });
        when(recipientRepository.saveAll(any())).thenAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(subscriberRepository.findAllActiveNotBlocked(TENANT_B)).thenReturn(List.of(sub(1L, "igsid-1")));
        when(campaignRepository.findByIdAndRestaurantId(10L, TENANT_B))
                .thenAnswer(inv -> Optional.ofNullable(savedCampaign.get()));

        service.createAndSend(request("promo", "ALL"));

        // The request thread does NOT loop over Meta — it hands off and the campaign is left SENDING.
        verify(executor).executeCampaign(10L);
        assertThat(savedCampaign.get().getStatus()).isEqualTo(CampaignStatus.SENDING);
    }
}
