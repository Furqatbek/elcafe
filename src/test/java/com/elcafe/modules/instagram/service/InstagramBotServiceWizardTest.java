package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.entity.InstagramSubscriberAddress;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The registration wizard's behaviour on inputs it used to mishandle.
 *
 * <p>The 355-line wizard had no tests at all. Each case here is a defect the module audit found: a
 * blocked subscriber that kept talking to the bot, story engagement stored as a delivery address,
 * media that produced silence, an unbounded address list, and a profile reaching REGISTERED with no
 * phone number.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotServiceWizardTest {

    private static final Long RESTAURANT = 3L;
    private static final String IGSID = "igsid-1";

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramSubscriberAddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private InstagramApiClient apiClient;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    // A mock manager makes the service's TransactionTemplate run its callback inline (no real
    // transaction), so the wizard executes exactly as it does under a committed one — which is what
    // lets these unit tests exercise the DB-work-then-send split without a Spring context.
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private InstagramBotService service;

    private InstagramBotConfig config;

    @BeforeEach
    void setUp() {
        config = InstagramBotConfig.builder().restaurantId(RESTAURANT).isActive(true).build();
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.findAllBySubscriber(any())).thenReturn(List.of());
    }

    private InstagramSubscriber subscriber(String state) {
        InstagramSubscriber s = InstagramSubscriber.builder()
                .id(1L).restaurantId(RESTAURANT).igsid(IGSID)
                .displayName("Aziz").phone("+998901234567")
                .conversationState(state).isActive(true).isBlocked(false)
                .build();
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.of(s));
        return s;
    }

    private void send(InstagramInboundKind kind, String text, String payload) {
        service.handleIncomingMessage(config, IGSID, null, kind, text, payload);
    }

    @Test
    @DisplayName("a blocked subscriber gets no reply at all, not even to a restart keyword")
    void blockedSubscriberIsIgnored() {
        InstagramSubscriber blocked = subscriber("AWAITING_ADDRESS");
        blocked.setIsBlocked(true);

        send(InstagramInboundKind.TEXT, "hi", null);   // used to restart the wizard + re-welcome

        verify(apiClient, never()).sendMessage(any(), anyString(), anyString());
        verify(subscriberRepository, never()).save(any());
    }

    @Test
    @DisplayName("a story reply is never stored as a delivery address")
    void storyReplyDoesNotBecomeAnAddress() {
        subscriber("AWAITING_ADDRESS");

        send(InstagramInboundKind.STORY_REPLY, "🔥", null);

        verify(addressRepository, never()).save(any());
        // The thread does not just stop — the wizard restates what it was waiting for.
        verify(apiClient).sendMessage(any(), eq(IGSID), anyString());
    }

    @Test
    @DisplayName("a story reply from a stranger does not drag them into registration")
    void storyReplyFromUnknownUserDoesNotStartTheWizard() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.empty());

        send(InstagramInboundKind.STORY_REPLY, "😍", null);

        verify(subscriberRepository, never()).save(any());
        verify(apiClient, never()).sendMessage(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("media gets a re-prompt instead of silence")
    void unsupportedAttachmentRePrompts() {
        subscriber("AWAITING_PHONE");

        send(InstagramInboundKind.UNSUPPORTED_ATTACHMENT, null, null);

        verify(apiClient).sendMessage(any(), eq(IGSID), anyString());
    }

    @Test
    @DisplayName("addresses are capped, so the list cannot grow without bound")
    void addressListIsCapped() {
        subscriber("AWAITING_ADDRESS");
        when(addressRepository.countBySubscriber(any())).thenReturn(5L); // already at MAX_ADDRESSES

        send(InstagramInboundKind.TEXT, "Chilonzor 5, uy 12", null);

        verify(addressRepository, never()).save(any());
        verify(apiClient).sendMessage(any(), eq(IGSID), anyString());
    }

    @Test
    @DisplayName("a duplicate address is not appended again")
    void duplicateAddressIsRejected() {
        InstagramSubscriber s = subscriber("AWAITING_ADDRESS");
        when(addressRepository.countBySubscriber(any())).thenReturn(1L);
        when(addressRepository.findAllBySubscriber(any())).thenReturn(List.of(
                InstagramSubscriberAddress.builder()
                        .restaurantId(RESTAURANT).subscriber(s).address("Chilonzor 5, uy 12").build()));

        send(InstagramInboundKind.TEXT, "  chilonzor 5, UY 12 ", null);   // same address, different case

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("short junk is not accepted as an address")
    void tooShortTextIsNotAnAddress() {
        subscriber("AWAITING_ADDRESS");

        send(InstagramInboundKind.TEXT, "ok", null);

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("\"no\" while being asked for more addresses ends the wizard instead of being saved")
    void negativeAnswerDoesNotBecomeAnAddress() {
        InstagramSubscriber s = subscriber("AWAITING_MORE_ADDRESSES");

        send(InstagramInboundKind.TEXT, "yo'q", null);

        verify(addressRepository, never()).save(any());
        assertThat(s.getConversationState()).isEqualTo("REGISTERED");
    }

    @Test
    @DisplayName("a stale \"Done\" cannot complete a half-finished wizard")
    void quickReplyIsGatedOnState() {
        InstagramSubscriber s = subscriber("AWAITING_PHONE");

        send(InstagramInboundKind.QUICK_REPLY, null, "DONE");   // tapped an old bubble

        assertThat(s.getConversationState()).isEqualTo("AWAITING_PHONE");
    }

    @Test
    @DisplayName("registration cannot complete without a phone number")
    void completionRequiresAPhone() {
        InstagramSubscriber s = subscriber("AWAITING_MORE_ADDRESSES");
        s.setPhone(null);

        send(InstagramInboundKind.QUICK_REPLY, null, "DONE");

        assertThat(s.getConversationState()).isEqualTo("AWAITING_PHONE");
    }

    @Test
    @DisplayName("the Graph send happens AFTER the transaction commits, never while it holds a connection")
    void sendHappensAfterCommit() {
        // A brand-new sender saying "hi": the wizard writes a subscriber row, then welcomes them.
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.empty());

        send(InstagramInboundKind.TEXT, "hi", null);

        // Finding #10 was that the send ran inside the @Transactional method, so a pooled Hikari
        // connection stayed checked out for the whole 5s+10s Meta round-trip. The fix returns the
        // reply from the transaction and sends after it commits — pin that ordering so moving the
        // Graph call back inside the transaction fails here rather than silently in production.
        InOrder inOrder = inOrder(transactionManager, apiClient);
        inOrder.verify(transactionManager).commit(any());
        inOrder.verify(apiClient).sendMessage(any(), eq(IGSID), anyString());
    }

    @Test
    @DisplayName("a transaction that fails to commit sends nothing — no phantom \"✅ saqlandi\" for an unsaved row")
    void noSendWhenCommitFails() {
        // Finding #10's second half: because the pre-fix wizard sent inside the transaction, a failure
        // AFTER the "✅ Manzil saqlandi" send left the customer confirmed for a row that then rolled
        // back. With DB-work-then-send, the commit is attempted BEFORE any Graph call — so if the
        // transaction cannot commit (constraint on flush, dropped connection), the send is never
        // reached and the customer is never told about a row that does not exist. Move the dispatch
        // back inside the transaction and this goes red: the send fires, then the commit throws.
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("commit failed")).when(transactionManager).commit(any());

        assertThatThrownBy(() -> send(InstagramInboundKind.TEXT, "hi", null))
                .isInstanceOf(RuntimeException.class);

        verify(apiClient, never()).sendMessage(any(), anyString(), anyString());
        verify(apiClient, never()).sendMessageWithQuickReplies(any(), anyString(), anyString(), any());
    }
}
