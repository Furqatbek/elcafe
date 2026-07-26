package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V174: opt-out (STOP) / opt-in (START/SUBSCRIBE) keyword handling — the Meta-app-restriction fix.
 * Before this, {@code broadcast("ALL")} messaged everyone who ever DM'd (including someone mid-wizard
 * who never consented), and a user typing STOP was fed back into the registration wizard (stored as
 * their name, phone, address, ... depending on what state they were in) instead of being unsubscribed.
 *
 * <p>Mirrors {@link InstagramBotServiceWizardTest}'s Mockito setup: a mocked {@link
 * PlatformTransactionManager} runs {@code txTemplate}'s callback inline, so {@code handleIncomingMessage}
 * exercises the real DB-work-then-send split without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotServiceOptOutTest {

    private static final Long RESTAURANT = 3L;
    private static final String IGSID = "igsid-optout-1";

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramSubscriberAddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;
    // Unused by any test here (STOP/opt-in never reach the away-note check — see
    // InstagramBotServiceAwayMessageTest) but declared so @InjectMocks constructor-wires a real mock
    // rather than leaving the field null.
    @Mock private BusinessHoursRepository businessHoursRepository;
    @Mock private InstagramApiClient apiClient;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private InstagramMessageLogger messageLogger;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private InstagramBotService service;

    private InstagramBotConfig config;

    @BeforeEach
    void setUp() {
        config = InstagramBotConfig.builder().restaurantId(RESTAURANT).isActive(true).build();
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.findAllBySubscriber(any())).thenReturn(List.of());
    }

    private InstagramSubscriber subscriber(String state, boolean marketingOptIn) {
        InstagramSubscriber s = InstagramSubscriber.builder()
                .id(1L).restaurantId(RESTAURANT).igsid(IGSID)
                .displayName(null).phone(null)
                .conversationState(state).isActive(true).isBlocked(false)
                .marketingOptIn(marketingOptIn)
                .build();
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.of(s));
        return s;
    }

    private void send(String text) {
        service.handleIncomingMessage(config, IGSID, null, InstagramInboundKind.TEXT, text, null);
    }

    private String sentText() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).sendMessage(any(), eq(IGSID), captor.capture());
        return captor.getValue();
    }

    // -------------------------------------------------------------------------
    // STOP (opt-out)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("STOP from a mid-wizard subscriber opts out, replies, and does NOT advance the wizard")
    void stopOptsOutAndDoesNotAdvanceWizard() {
        InstagramSubscriber s = subscriber("AWAITING_NAME", true);

        send("STOP");

        assertThat(s.getMarketingOptIn()).isFalse();
        assertThat(s.getOptedOutAt()).isNotNull();
        // Never advanced past AWAITING_NAME, and never stored as their name.
        assertThat(s.getConversationState()).isEqualTo("AWAITING_NAME");
        assertThat(s.getDisplayName()).isNull();
        verify(apiClient).sendMessage(any(), eq(IGSID), anyString());
    }

    @Test
    @DisplayName("STOP is never treated as the user's name, even case-insensitively / with whitespace")
    void stopIsNeverTheUsersName() {
        InstagramSubscriber s = subscriber("AWAITING_NAME", true);

        send("  stop  ");

        assertThat(s.getDisplayName()).isNull();
        assertThat(s.getMarketingOptIn()).isFalse();
    }

    @Test
    @DisplayName("STOP from a REGISTERED subscriber still opts out, not just the mid-wizard case")
    void stopFromRegisteredSubscriberOptsOut() {
        InstagramSubscriber s = subscriber("REGISTERED", true);
        s.setDisplayName("Aziz");
        s.setPhone("+998901234567");

        send("unsubscribe");

        assertThat(s.getMarketingOptIn()).isFalse();
        assertThat(s.getOptedOutAt()).isNotNull();
        assertThat(s.getConversationState()).isEqualTo("REGISTERED"); // untouched
    }

    @Test
    @DisplayName("STOP from a total stranger replies but creates no subscriber row at all")
    void stopFromBrandNewSenderCreatesNoSubscriber() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.empty());

        send("STOP");

        verify(subscriberRepository, never()).save(any());
        // Still replies — STOP always gets an answer, whether or not there was ever a subscription.
        verify(apiClient).sendMessage(any(), eq(IGSID), anyString());
    }

    @Test
    @DisplayName("opt-out wins over wizard-start: a stranger's first message being STOP is never the wizard welcome")
    void optOutPreemptsWizardWelcomeForNewSender() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.empty());

        send("stop");

        // Not the "👋 Xush kelibsiz" wizard welcome, and no name prompt either.
        assertThat(sentText()).doesNotContain("Xush kelibsiz").doesNotContain("Ismingizni kiriting");
    }

    @Test
    @DisplayName("Uzbek opt-out synonyms (to'xtat / toxtat / bekor) all opt out")
    void uzbekOptOutSynonymsWork() {
        for (String word : List.of("to'xtat", "toxtat", "bekor")) {
            InstagramSubscriber s = subscriber("REGISTERED", true);

            send(word);

            assertThat(s.getMarketingOptIn())
                    .as("keyword '%s' should opt the subscriber out", word)
                    .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // START / SUBSCRIBE / obuna (re-opt-in)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("START re-opts-in an existing opted-out subscriber and restarts the wizard")
    void startReOptsInAndRestartsWizard() {
        InstagramSubscriber s = subscriber("REGISTERED", false);   // previously opted out
        s.setOptedOutAt(java.time.OffsetDateTime.now());

        send("start");

        assertThat(s.getMarketingOptIn()).isTrue();
        assertThat(s.getOptedOutAt()).isNull();
        // "start" keeps its existing restart-keyword behaviour intact.
        assertThat(s.getConversationState()).isEqualTo("AWAITING_NAME");
    }

    @Test
    @DisplayName("SUBSCRIBE opts back in WITHOUT touching wizard state — unlike \"start\"")
    void subscribeOptsInWithoutRestartingWizard() {
        InstagramSubscriber s = subscriber("AWAITING_PHONE", false);   // mid-wizard AND opted out

        send("SUBSCRIBE");

        assertThat(s.getMarketingOptIn()).isTrue();
        assertThat(s.getOptedOutAt()).isNull();
        // Never advanced/reset — SUBSCRIBE is not wizard input.
        assertThat(s.getConversationState()).isEqualTo("AWAITING_PHONE");
        assertThat(s.getPhone()).isNull();
    }

    @Test
    @DisplayName("the Uzbek \"obuna\" keyword also opts back in without touching wizard state")
    void obunaOptsInWithoutRestartingWizard() {
        InstagramSubscriber s = subscriber("AWAITING_ADDRESS", false);

        send("obuna");

        assertThat(s.getMarketingOptIn()).isTrue();
        assertThat(s.getOptedOutAt()).isNull();
        assertThat(s.getConversationState()).isEqualTo("AWAITING_ADDRESS");
    }

    @Test
    @DisplayName("\"hi\" restarts the wizard but does NOT silently re-opt-in an opted-out subscriber")
    void hiDoesNotChangeOptInStatus() {
        InstagramSubscriber s = subscriber("REGISTERED", false);

        send("hi");

        // Restarted (existing restart-keyword behaviour, unchanged)...
        assertThat(s.getConversationState()).isEqualTo("AWAITING_NAME");
        // ...but casual re-engagement is not explicit re-consent: still opted out.
        assertThat(s.getMarketingOptIn()).isFalse();
    }

    @Test
    @DisplayName("SUBSCRIBE from a stranger with no prior row still replies, but creates nothing")
    void subscribeFromStrangerCreatesNoRow() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.empty());

        send("subscribe");

        verify(subscriberRepository, never()).save(any());
        verify(apiClient).sendMessage(any(), eq(IGSID), anyString());
    }

    // -------------------------------------------------------------------------
    // Grandfathering: default-true keeps a never-explicitly-consented, mid-wizard subscriber reachable
    // until they actually say STOP. This is the BotService-side half; InstagramSubscriberRepositoryTest
    // proves the campaign-audience-finder side of the same guarantee.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a brand-new subscriber defaults to marketingOptIn=true (grandfathered reach)")
    void newSubscriberDefaultsToOptedIn() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.empty());

        send("hi");   // starts the wizard as a brand-new sender

        ArgumentCaptor<InstagramSubscriber> captor = ArgumentCaptor.forClass(InstagramSubscriber.class);
        verify(subscriberRepository).save(captor.capture());
        assertThat(captor.getValue().getMarketingOptIn()).isTrue();
    }

    @Test
    @DisplayName("that same never-consented subscriber is excluded the moment they say STOP")
    void grandfatheredSubscriberExcludedAfterStop() {
        // Mid-wizard, never finished registering, never explicitly consented — grandfathered true.
        InstagramSubscriber s = subscriber("AWAITING_PHONE", true);

        send("STOP");

        assertThat(s.getMarketingOptIn()).isFalse();
    }
}
