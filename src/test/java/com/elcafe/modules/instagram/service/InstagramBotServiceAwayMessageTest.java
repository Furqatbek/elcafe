package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.restaurant.entity.BusinessHours;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The working-hours away message: a customer who DMs outside business hours used to be pushed
 * straight into the registration wizard with no indication the restaurant was closed. These pin
 * {@code InstagramBotService.awayNoteIfClosed}, exercised only through the public {@code
 * handleIncomingMessage} entry point like every other test in this package — the note fires only for
 * a confident "closed" answer, it never blocks the wizard, and it never rides along with the
 * STOP/opt-in/story-engagement replies that already return from {@code process} before it is ever
 * consulted (see {@code InstagramBotService}'s class javadoc, "Working-hours away note").
 *
 * <p>Mirrors {@link InstagramBotServiceWizardTest}'s Mockito setup (a mocked {@link
 * PlatformTransactionManager} runs {@code txTemplate}'s callback inline) and {@code
 * ShiftTimeServiceTest}'s {@link MockedStatic} technique for pinning {@code LocalDate.now()} / {@code
 * LocalTime.now()} to a fixed instant, so "outside hours" vs "inside hours" is deterministic
 * regardless of when the suite actually runs — the same rows and the same clock convention {@code
 * ShiftTimeService#getCurrentBusinessDay} already uses elsewhere in the app.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotServiceAwayMessageTest {

    private static final Long RESTAURANT = 3L;
    private static final String IGSID = "igsid-hours-1";

    // An arbitrary fixed Wednesday, 14:00 — the default clock every test is pinned to unless a test
    // overrides NOW for its own scenario (e.g. the overnight cases).
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 25);
    private static final DayOfWeek TODAY_DOW = TODAY.getDayOfWeek();
    private static final LocalTime NOW = LocalTime.of(14, 0);

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramSubscriberAddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;
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

    /** Stub today's-or-any-day's business hours row. Called BEFORE the clock is pinned in {@link
     *  #sendWithClock}, so the {@code LocalTime.of(...)} calls inside this never race a static mock. */
    private void hours(DayOfWeek day, LocalTime open, LocalTime close, boolean closed) {
        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(RESTAURANT, day))
                .thenReturn(Optional.of(BusinessHours.builder()
                        .dayOfWeek(day).openTime(open).closeTime(close).closed(closed).build()));
    }

    /**
     * Send one inbound message with {@code LocalDate.now()} / {@code LocalTime.now()} pinned to a
     * fixed instant for the duration of the call — same technique {@code ShiftTimeServiceTest} uses to
     * make {@code getCurrentBusinessDay} deterministic. Scoped narrowly (try-with-resources) so it
     * cannot leak into other tests or interfere with fixture construction elsewhere in the test.
     */
    private void sendWithClock(LocalDate date, LocalTime time, InstagramInboundKind kind, String text,
                               String payload) {
        try (MockedStatic<LocalDate> mockedDate = mockStatic(LocalDate.class);
             MockedStatic<LocalTime> mockedTime = mockStatic(LocalTime.class)) {
            mockedDate.when(LocalDate::now).thenReturn(date);
            mockedTime.when(LocalTime::now).thenReturn(time);
            service.handleIncomingMessage(config, IGSID, null, kind, text, payload);
        }
    }

    /** Convenience overload: the default TODAY (Wednesday) / NOW (14:00) fixed clock. */
    private void send(InstagramInboundKind kind, String text, String payload) {
        sendWithClock(TODAY, NOW, kind, text, payload);
    }

    private String sentText() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).sendMessage(any(), eq(IGSID), captor.capture());
        return captor.getValue();
    }

    // -------------------------------------------------------------------------
    // Outside hours: the away note fires, and the wizard is NOT aborted
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("before today's opening: away note is prepended and the wizard still starts")
    void beforeOpeningAddsAwayNoteAndStillStartsWizard() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        hours(TODAY_DOW, LocalTime.of(16, 0), LocalTime.of(23, 0), false); // opens at 16:00; NOW is 14:00

        send(InstagramInboundKind.TEXT, "hi", null);

        String text = sentText();
        assertThat(text).contains("yopiqmiz");             // away note fired
        assertThat(text).contains("16:00");                // next opening time quoted
        assertThat(text).contains("Ismingizni kiriting");  // wizard still started (name prompt), not aborted

        ArgumentCaptor<InstagramSubscriber> captor = ArgumentCaptor.forClass(InstagramSubscriber.class);
        verify(subscriberRepository).save(captor.capture());
        assertThat(captor.getValue().getConversationState()).isEqualTo("AWAITING_NAME"); // wizard advanced
    }

    @Test
    @DisplayName("the motivating scenario: a 02:00 DM with 09:00-22:00 hours is told hours start at 09:00")
    void twoAmDmGetsAwayNoteWithNineAmOpening() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        hours(TODAY_DOW, LocalTime.of(9, 0), LocalTime.of(22, 0), false);

        sendWithClock(TODAY, LocalTime.of(2, 0), InstagramInboundKind.TEXT, "hi", null);

        String text = sentText();
        assertThat(text).contains("yopiqmiz").contains("09:00").contains("Ismingizni kiriting");
    }

    @Test
    @DisplayName("after today's closing: away note quotes TOMORROW's opening time")
    void afterClosingQuotesTomorrowsOpening() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        hours(TODAY_DOW, LocalTime.of(9, 0), LocalTime.of(13, 0), false);          // closed at 13:00; NOW is 14:00
        hours(TODAY_DOW.plus(1), LocalTime.of(9, 30), LocalTime.of(22, 0), false); // tomorrow opens 09:30

        send(InstagramInboundKind.TEXT, "hi", null);

        assertThat(sentText()).contains("yopiqmiz").contains("09:30").contains("Ertaga");
    }

    @Test
    @DisplayName("a quick-reply wizard step keeps its buttons even with an away note prepended")
    void awayNoteDoesNotStripQuickReplies() {
        subscriber("AWAITING_ADDRESS");
        when(addressRepository.countBySubscriber(any())).thenReturn(0L);
        hours(TODAY_DOW, LocalTime.of(16, 0), LocalTime.of(23, 0), false); // closed right now

        send(InstagramInboundKind.TEXT, "Chilonzor 5, uy 12", null);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).sendMessageWithQuickReplies(any(), eq(IGSID), textCaptor.capture(), any());
        assertThat(textCaptor.getValue()).contains("yopiqmiz").contains("Manzil saqlandi");
    }

    // -------------------------------------------------------------------------
    // Inside hours: byte-identical to before this feature
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("inside business hours: reply is byte-identical to the same message with no hours row at all")
    void insideHoursReplyMatchesNoHoursBaseline() {
        // Baseline: no business-hours row for today at all — unconditionally "before this feature".
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        send(InstagramInboundKind.TEXT, "hi", null);
        String baseline = sentText();

        reset(apiClient, subscriberRepository, addressRepository);
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.findAllBySubscriber(any())).thenReturn(List.of());
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        hours(TODAY_DOW, LocalTime.of(9, 0), LocalTime.of(22, 0), false); // NOW (14:00) is inside 9-22

        send(InstagramInboundKind.TEXT, "hi", null);
        String insideHours = sentText();

        assertThat(insideHours).doesNotContain("yopiqmiz");
        assertThat(insideHours).isEqualTo(baseline);
    }

    // -------------------------------------------------------------------------
    // Missing / closed-day / unknown hours: no away note, no crash
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("no business-hours row for today: no away note, no crash (treated as unknown)")
    void missingHoursNoAwayNote() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        // businessHoursRepository is left entirely unstubbed -> Optional.empty() by Mockito default.

        send(InstagramInboundKind.TEXT, "hi", null);

        assertThat(sentText()).doesNotContain("yopiqmiz");
    }

    @Test
    @DisplayName("today explicitly marked closed=true: no away note, no crash (not a confident close, see javadoc)")
    void closedDayNoAwayNote() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        // Deliberately a window NOW (14:00) falls OUTSIDE of (opens 16:00) — so the closed=true flag is
        // the ONLY thing standing between this fixture and a fired note; a fixture where "now" already
        // fell inside the hours would pass even if the closed-flag check were deleted entirely.
        hours(TODAY_DOW, LocalTime.of(16, 0), LocalTime.of(23, 0), true); // closed=true

        send(InstagramInboundKind.TEXT, "hi", null);

        assertThat(sentText()).doesNotContain("yopiqmiz");
    }

    @Test
    @DisplayName("a business-hours lookup failure degrades to no note — never a thrown webhook exception")
    void hoursLookupFailureDegradesGracefully() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        when(businessHoursRepository.findByRestaurant_IdAndDayOfWeek(eq(RESTAURANT), any()))
                .thenThrow(new RuntimeException("DB is down"));

        assertThatCode(() -> send(InstagramInboundKind.TEXT, "hi", null)).doesNotThrowAnyException();

        assertThat(sentText()).doesNotContain("yopiqmiz"); // no note, but the wizard still replied
    }

    // -------------------------------------------------------------------------
    // Overnight ranges (closeTime before openTime)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("overnight range: a 01:00 message is covered by YESTERDAY's 22:00-03:00 shift — no away note")
    void overnightRangeStillOpenFromYesterday() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        hours(TODAY_DOW.minus(1), LocalTime.of(22, 0), LocalTime.of(3, 0), false); // yesterday crosses midnight
        // TODAY's own row deliberately does NOT cover 01:00 (opens later, at 10:00): if the
        // yesterday-carryover check were skipped or broken, the code would fall through to evaluate
        // this row instead, find 01:00 "before opening", and incorrectly fire a note. Stubbing it
        // closes the gap a fixture with no today-row at all would leave (that would stay silent either
        // way, via the separate missing-hours guard, and so would not actually exercise this guard).
        hours(TODAY_DOW, LocalTime.of(10, 0), LocalTime.of(23, 0), false);

        sendWithClock(TODAY, LocalTime.of(1, 0), InstagramInboundKind.TEXT, "hi", null);

        assertThat(sentText()).doesNotContain("yopiqmiz");
    }

    @Test
    @DisplayName("overnight range: before tonight's opening, away note quotes TODAY's later opening time")
    void overnightRangeBeforeTonightsOpeningFiresAwayNote() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        // NOW defaults to 14:00; today's own hours are overnight (22:00-03:00), yesterday is unstubbed
        // (no bleed-through), so 14:00 is before tonight's 22:00 opening.
        hours(TODAY_DOW, LocalTime.of(22, 0), LocalTime.of(3, 0), false);

        send(InstagramInboundKind.TEXT, "hi", null);

        assertThat(sentText()).contains("yopiqmiz").contains("22:00");
    }

    @Test
    @DisplayName("overnight range: 23:00 is inside TODAY's own 22:00-03:00 shift — no away note")
    void overnightRangeOpenViaTodaysOwnShift() {
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT)).thenReturn(Optional.empty());
        hours(TODAY_DOW, LocalTime.of(22, 0), LocalTime.of(3, 0), false);

        sendWithClock(TODAY, LocalTime.of(23, 0), InstagramInboundKind.TEXT, "hi", null);

        assertThat(sentText()).doesNotContain("yopiqmiz");
    }

    // -------------------------------------------------------------------------
    // Non-interference: STOP and story engagement never see the away-note logic
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("STOP still opts out even when the restaurant is closed — the away note never rides along")
    void stopStillOptsOutRegardlessOfClosedHours() {
        InstagramSubscriber s = subscriber("AWAITING_NAME");
        hours(TODAY_DOW, LocalTime.of(16, 0), LocalTime.of(23, 0), false); // closed right now, if it were checked

        send(InstagramInboundKind.TEXT, "STOP", null);

        assertThat(s.getMarketingOptIn()).isFalse();
        assertThat(s.getConversationState()).isEqualTo("AWAITING_NAME"); // never entered the wizard either
        assertThat(sentText()).doesNotContain("yopiqmiz");
        verifyNoInteractions(businessHoursRepository); // proves the away-note path was never consulted
    }

    @Test
    @DisplayName("a story mention is unaffected by closed hours — no away note, no business-hours lookup")
    void storyMentionUnaffectedByClosedHours() {
        subscriber("AWAITING_ADDRESS");
        hours(TODAY_DOW, LocalTime.of(16, 0), LocalTime.of(23, 0), false); // closed right now, if it were checked

        send(InstagramInboundKind.STORY_MENTION, null, null);

        assertThat(sentText()).doesNotContain("yopiqmiz");
        verifyNoInteractions(businessHoursRepository);
    }

    @Test
    @DisplayName("SUBSCRIBE (re-opt-in) is unaffected by closed hours either")
    void optInUnaffectedByClosedHours() {
        subscriber("AWAITING_ADDRESS").setMarketingOptIn(false);
        hours(TODAY_DOW, LocalTime.of(16, 0), LocalTime.of(23, 0), false); // closed right now, if it were checked

        send(InstagramInboundKind.TEXT, "SUBSCRIBE", null);

        assertThat(sentText()).doesNotContain("yopiqmiz");
        verifyNoInteractions(businessHoursRepository);
    }
}
