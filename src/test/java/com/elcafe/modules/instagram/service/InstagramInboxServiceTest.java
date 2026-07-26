package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.instagram.dto.InstagramConversationMessageResponse;
import com.elcafe.modules.instagram.dto.InstagramConversationResponse;
import com.elcafe.modules.instagram.dto.InstagramConversationSummaryResponse;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.dto.InstagramSubscriberResponse;
import com.elcafe.modules.instagram.entity.InstagramInboundMessage;
import com.elcafe.modules.instagram.entity.InstagramLog;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.instagram.repository.InstagramInboundMessageRepository;
import com.elcafe.modules.instagram.repository.InstagramLogRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.sms.enums.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V179: {@link InstagramInboxService} — listing recent conversations, one conversation's merged
 * history, and the take-over/release human-handoff actions. {@link InstagramInboxService} is built
 * with an explicit constructor (not {@code @RequiredArgsConstructor}, because of the {@code @Value}
 * primitive {@code defaultHandoffHours}), so it is instantiated by hand here rather than via
 * {@code @InjectMocks} — giving the test a known, explicit default-hours value to assert against
 * instead of relying on Mockito's primitive-default behaviour.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramInboxServiceTest {

    private static final Long TENANT = 5L;
    private static final Long OTHER  = 6L;
    private static final long DEFAULT_HOURS = 2L;
    private static final long MAX_HANDOFF_HOURS = 24L * 30;

    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramInboundMessageRepository inboundMessageRepository;
    @Mock private InstagramLogRepository logRepository;
    @Mock private InstagramBotService botService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;

    private InstagramInboxService service;

    @BeforeEach
    void setUp() {
        service = new InstagramInboxService(subscriberRepository, inboundMessageRepository, logRepository,
                botService, restaurantAuthorizationService, DEFAULT_HOURS);
    }

    private InstagramSubscriber subscriber(Long id, OffsetDateTime handoffUntilOrNull) {
        return InstagramSubscriber.builder()
                .id(id).restaurantId(TENANT).igsid("ig" + id).displayName("Sub " + id)
                .humanHandoffUntil(handoffUntilOrNull).build();
    }

    /** A real row that exists — just under a DIFFERENT tenant. Reachable only via the unscoped {@code
     *  findById}, never via {@code findByIdAndRestaurantId(id, TENANT)} — the fixture the "foreign id"
     *  tests below need to actually distinguish correct tenant-scoped code from a bypass, unlike a
     *  plain not-stubbed id (which reads as not-found under BOTH). */
    private InstagramSubscriber foreignSubscriber(Long id) {
        return InstagramSubscriber.builder().id(id).restaurantId(OTHER).igsid("foreign-ig" + id).build();
    }

    // -------------------------------------------------------------------------
    // listConversations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("listConversations requires a restaurant-scoped caller — a platform account (null tenant) is rejected")
    void listConversations_rejectsUnscopedCaller() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(null);

        assertThatThrownBy(() -> service.listConversations(PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("listConversations builds one summary per conversation, preserving the most-recent-first "
            + "order and attaching each one's preview")
    void listConversations_buildsSummariesInOrderWithPreviews() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        Pageable pageable = PageRequest.of(0, 20);
        // Subscriber 2's thread is more recent than subscriber 1's — the id order below IS the contract.
        Page<Long> idsPage = new PageImpl<>(List.of(2L, 1L), pageable, 2);
        when(inboundMessageRepository.findRecentConversationSubscriberIds(TENANT, pageable)).thenReturn(idsPage);

        InstagramSubscriber s1 = subscriber(1L, null);
        InstagramSubscriber s2 = subscriber(2L, null);
        when(subscriberRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(s1, s2));

        InstagramInboundMessage preview1 = InstagramInboundMessage.builder()
                .subscriber(s1).messageText("hi from sub1").receivedAt(OffsetDateTime.now().minusMinutes(30)).build();
        InstagramInboundMessage preview2 = InstagramInboundMessage.builder()
                .subscriber(s2).messageText("hi from sub2").receivedAt(OffsetDateTime.now().minusMinutes(1)).build();
        when(inboundMessageRepository.findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc(TENANT, List.of(2L, 1L)))
                .thenReturn(List.of(preview2, preview1));

        Page<InstagramConversationSummaryResponse> page = service.listConversations(pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getSubscriberId()).isEqualTo(2L);
        assertThat(page.getContent().get(0).getPreview()).isEqualTo("hi from sub2");
        assertThat(page.getContent().get(1).getSubscriberId()).isEqualTo(1L);
        assertThat(page.getContent().get(1).getPreview()).isEqualTo("hi from sub1");
    }

    @Test
    @DisplayName("listConversations returns an empty page without further queries when there are no conversations")
    void listConversations_emptyWhenNothingToShow() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        Pageable pageable = PageRequest.of(0, 20);
        when(inboundMessageRepository.findRecentConversationSubscriberIds(TENANT, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<InstagramConversationSummaryResponse> page = service.listConversations(pageable);

        assertThat(page.getContent()).isEmpty();
        verify(subscriberRepository, never()).findAllById(any());
    }

    // -------------------------------------------------------------------------
    // getConversation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getConversation merges inbound and outbound rows into one chronological transcript")
    void getConversation_mergesChronologically() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        InstagramSubscriber sub = subscriber(10L, null);
        when(subscriberRepository.findByIdAndRestaurantId(10L, TENANT)).thenReturn(Optional.of(sub));

        OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(30);
        OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(20);
        OffsetDateTime t3 = OffsetDateTime.now().minusMinutes(10);

        InstagramInboundMessage in1 = InstagramInboundMessage.builder().messageText("hello").receivedAt(t1).build();
        InstagramInboundMessage in2 = InstagramInboundMessage.builder().messageText("thanks").receivedAt(t3).build();
        when(inboundMessageRepository.findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(TENANT, 10L))
                .thenReturn(List.of(in1, in2));

        InstagramLog outLog = InstagramLog.builder()
                .message("Welcome!").createdAt(t2)
                .messageType(InstagramMessageType.AUTOMATION).status(MessageStatus.SENT).build();
        when(logRepository.findBySubscriberIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(outLog));

        InstagramConversationResponse result = service.getConversation(10L);

        assertThat(result.getSubscriber().getId()).isEqualTo(10L);
        List<InstagramConversationMessageResponse> messages = result.getMessages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getText()).isEqualTo("hello");
        assertThat(messages.get(0).getDirection()).isEqualTo(InstagramConversationMessageResponse.Direction.IN);
        assertThat(messages.get(1).getText()).isEqualTo("Welcome!");
        assertThat(messages.get(1).getDirection()).isEqualTo(InstagramConversationMessageResponse.Direction.OUT);
        assertThat(messages.get(1).getMessageType()).isEqualTo("AUTOMATION");
        assertThat(messages.get(1).getStatus()).isEqualTo("SENT");
        assertThat(messages.get(2).getText()).isEqualTo("thanks");
        assertThat(messages.get(2).getDirection()).isEqualTo(InstagramConversationMessageResponse.Direction.IN);
    }

    @Test
    @DisplayName("getConversation on a foreign subscriber id reads as not-found (IDOR closed)")
    void getConversation_foreignId_isNotFound() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        // The id genuinely exists — just under OTHER's tenant — so a scoping bypass that fell back to
        // an unscoped findById would find it; only the correct tenant-scoped query correctly misses it.
        when(subscriberRepository.findById(99L)).thenReturn(Optional.of(foreignSubscriber(99L)));
        when(subscriberRepository.findByIdAndRestaurantId(99L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getConversation for a platform (SUPER_ADMIN) caller looks up unscoped, by id alone")
    void getConversation_superAdmin_looksUpUnscoped() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(null);
        InstagramSubscriber sub = subscriber(10L, null);
        when(subscriberRepository.findById(10L)).thenReturn(Optional.of(sub));
        when(inboundMessageRepository.findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(TENANT, 10L))
                .thenReturn(List.of());
        when(logRepository.findBySubscriberIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        InstagramConversationResponse result = service.getConversation(10L);

        assertThat(result.getSubscriber().getId()).isEqualTo(10L);
        verify(subscriberRepository, never()).findByIdAndRestaurantId(any(), any());
    }

    // -------------------------------------------------------------------------
    // takeover / release
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("takeover with no requested hours sets humanHandoffUntil to now + the configured default")
    void takeover_usesConfiguredDefault() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        InstagramSubscriber sub = subscriber(10L, null);
        when(subscriberRepository.findByIdAndRestaurantId(10L, TENANT)).thenReturn(Optional.of(sub));
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).plusHours(DEFAULT_HOURS);
        InstagramSubscriberResponse response = service.takeover(10L, null);
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).plusHours(DEFAULT_HOURS);

        assertThat(response.getHumanHandoffUntil()).isBetween(before.minusSeconds(5), after.plusSeconds(5));
        assertThat(sub.getHumanHandoffUntil()).isEqualTo(response.getHumanHandoffUntil());
        verify(subscriberRepository).save(sub);
    }

    @Test
    @DisplayName("takeover with a requested hours override uses that instead of the default")
    void takeover_respectsRequestedOverride() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        InstagramSubscriber sub = subscriber(10L, null);
        when(subscriberRepository.findByIdAndRestaurantId(10L, TENANT)).thenReturn(Optional.of(sub));
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).plusHours(8);
        InstagramSubscriberResponse response = service.takeover(10L, 8L);
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).plusHours(8);

        assertThat(response.getHumanHandoffUntil()).isBetween(before.minusSeconds(5), after.plusSeconds(5));
    }

    @Test
    @DisplayName("takeover clamps an excessive requested duration rather than overflowing or failing")
    void takeover_clampsExcessiveHours() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        InstagramSubscriber sub = subscriber(10L, null);
        when(subscriberRepository.findByIdAndRestaurantId(10L, TENANT)).thenReturn(Optional.of(sub));
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramSubscriberResponse response = service.takeover(10L, Long.MAX_VALUE / 2);

        OffsetDateTime expected = OffsetDateTime.now(ZoneOffset.UTC).plusHours(MAX_HANDOFF_HOURS);
        assertThat(response.getHumanHandoffUntil()).isBetween(expected.minusMinutes(1), expected.plusMinutes(1));
    }

    @Test
    @DisplayName("takeover on a foreign subscriber id reads as not-found, tenant-scoped")
    void takeover_foreignId_isNotFound() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        // See getConversation_foreignId_isNotFound: the id is real, just under another tenant, so this
        // actually distinguishes correct scoping from a bypass rather than merely an unknown id.
        when(subscriberRepository.findById(99L)).thenReturn(Optional.of(foreignSubscriber(99L)));
        when(subscriberRepository.findByIdAndRestaurantId(99L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.takeover(99L, null)).isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).save(any());
    }

    @Test
    @DisplayName("release clears humanHandoffUntil back to null")
    void release_clearsTheFlag() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        InstagramSubscriber handedOff = subscriber(10L, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        when(subscriberRepository.findByIdAndRestaurantId(10L, TENANT)).thenReturn(Optional.of(handedOff));
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstagramSubscriberResponse response = service.release(10L);

        assertThat(response.getHumanHandoffUntil()).isNull();
        assertThat(handedOff.getHumanHandoffUntil()).isNull();
    }

    @Test
    @DisplayName("release on a foreign subscriber id reads as not-found, tenant-scoped")
    void release_foreignId_isNotFound() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        // See getConversation_foreignId_isNotFound: the id is real, just under another tenant, so this
        // actually distinguishes correct scoping from a bypass rather than merely an unknown id.
        when(subscriberRepository.findById(99L)).thenReturn(Optional.of(foreignSubscriber(99L)));
        when(subscriberRepository.findByIdAndRestaurantId(99L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // reply
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reply delegates straight to InstagramBotService#sendAdminMessage")
    void reply_delegatesToBotService() {
        when(botService.sendAdminMessage(10L, "hello there")).thenReturn(InstagramSendResult.ok());

        InstagramSendResult result = service.reply(10L, "hello there");

        assertThat(result.delivered()).isTrue();
        verify(botService).sendAdminMessage(eq(10L), eq("hello there"));
    }
}
