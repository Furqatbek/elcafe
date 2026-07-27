package com.elcafe.modules.instagram.scheduler;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramAutomationRule;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.entity.InstagramTemplate;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.instagram.enums.InstagramTriggerType;
import com.elcafe.modules.instagram.repository.InstagramAutomationRuleRepository;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.instagram.service.InstagramApiClient;
import com.elcafe.modules.instagram.service.InstagramMessageLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InstagramScheduler}'s birthday and win-back jobs: a matching subscriber gets the rule's
 * template rendered, sent, and logged as {@link InstagramMessageType#AUTOMATION}, and the rule's own
 * sentCount/lastTriggeredAt is bumped exactly once per run; a subscriber the finder query does NOT
 * return (e.g. a non-birthday one — the actual date-matching SQL is proven separately and authoritatively
 * in {@code InstagramSubscriberAutomationFinderRepositoryTest}) is never contacted at all.
 *
 * <p>All collaborators are mocked, mirroring {@code InstagramCampaignExecutorTest}'s style: this is a
 * pure unit test of the scheduler's OWN send-loop logic (config resolution, per-subscriber send/log,
 * fatal-failure halting, the rule's count bump) — not of the finder queries themselves.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramSchedulerTest {

    private static final Long TENANT = 7L;

    @Mock private InstagramAutomationRuleRepository automationRuleRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramBotConfigRepository botConfigRepository;
    @Mock private InstagramApiClient apiClient;
    @Mock private InstagramMessageLogger messageLogger;

    @InjectMocks private InstagramScheduler scheduler;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private InstagramTemplate template(String messageText) {
        return InstagramTemplate.builder().id(1L).restaurantId(TENANT).name("T")
                .messageText(messageText).isActive(true).build();
    }

    private InstagramAutomationRule rule(InstagramTriggerType triggerType, InstagramTemplate template) {
        return InstagramAutomationRule.builder()
                .id(42L).restaurantId(TENANT).name("Rule").triggerType(triggerType)
                .template(template).isActive(true).delayMinutes(0).sentCount(0).build();
    }

    private InstagramBotConfig activeConfig() {
        return InstagramBotConfig.builder().id(1L).restaurantId(TENANT).isActive(true).build();
    }

    private InstagramSubscriber subscriber(String igsid, String displayName) {
        return InstagramSubscriber.builder()
                .id(1L).restaurantId(TENANT).igsid(igsid).displayName(displayName)
                .isActive(true).isBlocked(false).build();
    }

    // ------------------------------------------------------------------ BIRTHDAY

    @Test
    @DisplayName("a birthday subscriber gets the rendered template sent and logged as AUTOMATION; "
            + "a non-birthday subscriber (never returned by the finder) is never contacted")
    void birthdaySubscriber_getsRenderedTemplateSentAndLogged_nonBirthdayIsNot() {
        InstagramTemplate template = template("Happy birthday, {name}! 🎂 Enjoy a gift on us.");
        InstagramAutomationRule rule = rule(InstagramTriggerType.BIRTHDAY, template);
        InstagramBotConfig config = activeConfig();
        // Seeded with birthDate = today, per the task's "seed birthDate = today" option — the finder
        // mock below is what actually determines who the scheduler sees, exactly as the real
        // findBirthdaysToday query would after filtering by month/day (proven in the dedicated
        // repository test); this object's birthDate documents WHY it would be selected.
        InstagramSubscriber birthdaySubscriber = subscriber("bday-igsid", "Alice");
        birthdaySubscriber.setBirthDate(LocalDate.now());
        InstagramSubscriber nonBirthdaySubscriber = subscriber("other-igsid", "Bob");
        nonBirthdaySubscriber.setBirthDate(LocalDate.now().plusMonths(6));

        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY))
                .thenReturn(List.of(rule));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.of(config));
        // Only the birthday subscriber is returned — the finder query itself already excluded the other.
        when(subscriberRepository.findBirthdaysToday(eq(TENANT), anyInt(), anyInt()))
                .thenReturn(List.of(birthdaySubscriber));
        when(apiClient.sendMessage(eq(config), eq("bday-igsid"), any())).thenReturn(InstagramSendResult.ok());

        scheduler.processBirthdayAutomation();

        // The finder was asked about TODAY specifically.
        ArgumentCaptor<Integer> monthCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> dayCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(subscriberRepository).findBirthdaysToday(eq(TENANT), monthCaptor.capture(), dayCaptor.capture());
        assertThat(monthCaptor.getValue()).isEqualTo(LocalDate.now().getMonthValue());
        assertThat(dayCaptor.getValue()).isEqualTo(LocalDate.now().getDayOfMonth());

        // The rendered (placeholder-substituted) template was sent to the birthday subscriber...
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).sendMessage(eq(config), eq("bday-igsid"), textCaptor.capture());
        assertThat(textCaptor.getValue()).isEqualTo("Happy birthday, Alice! 🎂 Enjoy a gift on us.");

        // ...logged as AUTOMATION with the rendered text and the delivered result...
        verify(messageLogger).record(eq(config), eq("bday-igsid"), eq(birthdaySubscriber),
                eq(InstagramMessageType.AUTOMATION), eq("Happy birthday, Alice! 🎂 Enjoy a gift on us."),
                argThat(InstagramSendResult::delivered), isNull());

        // ...and NEVER sent/logged to the non-birthday subscriber, who the finder never returned.
        verify(apiClient, never()).sendMessage(any(), eq("other-igsid"), any());
        verify(messageLogger, never()).record(any(), eq("other-igsid"), any(), any(), any(), any(), any());

        // The rule's own counters are bumped exactly once for this run.
        assertThat(rule.getSentCount()).isEqualTo(1);
        assertThat(rule.getLastTriggeredAt()).isNotNull();
        verify(automationRuleRepository, times(1)).save(rule);
    }

    @Test
    @DisplayName("no active BIRTHDAY rules: nothing is queried or sent")
    void noBirthdayRules_noWork() {
        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY))
                .thenReturn(List.of());

        scheduler.processBirthdayAutomation();

        verify(botConfigRepository, never()).findByRestaurantIdAndIsActiveTrue(any());
        verify(subscriberRepository, never()).findBirthdaysToday(any(), anyInt(), anyInt());
        verify(apiClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("no active Instagram config for the restaurant: the rule is skipped, nothing is sent")
    void noActiveConfig_ruleSkipped() {
        InstagramTemplate template = template("Happy birthday {name}!");
        InstagramAutomationRule rule = rule(InstagramTriggerType.BIRTHDAY, template);
        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY))
                .thenReturn(List.of(rule));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.empty());

        scheduler.processBirthdayAutomation();

        verify(subscriberRepository, never()).findBirthdaysToday(any(), anyInt(), anyInt());
        verify(apiClient, never()).sendMessage(any(), any(), any());
        verify(automationRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("a rule with no template is skipped defensively, without touching the subscriber finder")
    void ruleWithNoTemplateSkipped() {
        InstagramAutomationRule rule = InstagramAutomationRule.builder()
                .id(42L).restaurantId(TENANT).name("Broken Rule").triggerType(InstagramTriggerType.BIRTHDAY)
                .template(null).isActive(true).build();
        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY))
                .thenReturn(List.of(rule));

        scheduler.processBirthdayAutomation();

        verify(botConfigRepository, never()).findByRestaurantIdAndIsActiveTrue(any());
        verify(apiClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("a channel-fatal failure (dead token) halts the rest of THIS rule's sends")
    void fatalFailureHaltsRemainingSendsForThisRule() {
        InstagramTemplate template = template("Happy birthday {name}!");
        InstagramAutomationRule rule = rule(InstagramTriggerType.BIRTHDAY, template);
        InstagramBotConfig config = activeConfig();
        InstagramSubscriber first = subscriber("first", "First");
        InstagramSubscriber second = subscriber("second", "Second");

        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY))
                .thenReturn(List.of(rule));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.of(config));
        when(subscriberRepository.findBirthdaysToday(eq(TENANT), anyInt(), anyInt()))
                .thenReturn(List.of(first, second));
        when(apiClient.sendMessage(eq(config), eq("first"), any())).thenReturn(
                InstagramSendResult.failed(InstagramSendResult.Failure.TOKEN_INVALID, 190, "expired"));

        scheduler.processBirthdayAutomation();

        verify(apiClient).sendMessage(eq(config), eq("first"), any());
        verify(apiClient, never()).sendMessage(eq(config), eq("second"), any()); // halted before the second
        // Still logged — the attempt itself is auditable even though it failed.
        verify(messageLogger).record(eq(config), eq("first"), any(), eq(InstagramMessageType.AUTOMATION),
                any(), argThat(result -> !result.delivered()), isNull());
        // Nothing delivered this run — no count bump, no save.
        assertThat(rule.getSentCount()).isEqualTo(0);
        verify(automationRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("a per-recipient failure (e.g. outside the 24h window) is logged and the run continues")
    void perRecipientFailureContinuesTheRun() {
        InstagramTemplate template = template("Happy birthday {name}!");
        InstagramAutomationRule rule = rule(InstagramTriggerType.BIRTHDAY, template);
        InstagramBotConfig config = activeConfig();
        InstagramSubscriber outOfWindow = subscriber("stale", "Stale");
        InstagramSubscriber inWindow = subscriber("fresh", "Fresh");

        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY))
                .thenReturn(List.of(rule));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.of(config));
        when(subscriberRepository.findBirthdaysToday(eq(TENANT), anyInt(), anyInt()))
                .thenReturn(List.of(outOfWindow, inWindow));
        // Meta's real-world rejection for "hasn't messaged in the last 24h" — see class javadoc.
        when(apiClient.sendMessage(eq(config), eq("stale"), any())).thenReturn(
                InstagramSendResult.failed(InstagramSendResult.Failure.RECIPIENT_UNAVAILABLE, 10, "outside window"));
        when(apiClient.sendMessage(eq(config), eq("fresh"), any())).thenReturn(InstagramSendResult.ok());

        scheduler.processBirthdayAutomation();

        verify(apiClient).sendMessage(eq(config), eq("stale"), any());
        verify(apiClient).sendMessage(eq(config), eq("fresh"), any()); // run continued to the second
        verify(messageLogger, times(2)).record(any(), any(), any(), eq(InstagramMessageType.AUTOMATION),
                any(), any(), isNull());
        assertThat(rule.getSentCount()).isEqualTo(1); // only the delivered one counted
        verify(automationRuleRepository).save(rule);
    }

    @Test
    @DisplayName("the send runs under the rule's own tenant, and the context is cleared afterwards")
    void tenantContextBoundDuringSendAndClearedAfter() {
        InstagramTemplate template = template("Happy birthday {name}!");
        InstagramAutomationRule rule = rule(InstagramTriggerType.BIRTHDAY, template);
        InstagramBotConfig config = activeConfig();
        InstagramSubscriber subscriber = subscriber("igsid", "Alice");

        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY))
                .thenReturn(List.of(rule));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.of(config));
        when(subscriberRepository.findBirthdaysToday(eq(TENANT), anyInt(), anyInt()))
                .thenReturn(List.of(subscriber));
        AtomicReference<Long> boundDuringSend = new AtomicReference<>();
        when(apiClient.sendMessage(any(), any(), any())).thenAnswer(inv -> {
            boundDuringSend.set(TenantContext.getRestaurantId());
            return InstagramSendResult.ok();
        });

        scheduler.processBirthdayAutomation();

        assertThat(boundDuringSend.get())
                .as("the send runs under the rule's restaurant, not an unset context")
                .isEqualTo(TENANT);
        assertThat(TenantContext.getRestaurantId())
                .as("cleared after the run — nothing leaks onto the next pooled task")
                .isNull();
    }

    // ------------------------------------------------------------------ WIN_BACK

    @Test
    @DisplayName("win-back: an inactive subscriber gets the rendered template sent and logged")
    void winBackSubscriber_getsRenderedTemplateSentAndLogged() {
        InstagramTemplate template = template("We miss you, {name}! Come back for 15% off.");
        InstagramAutomationRule rule = rule(InstagramTriggerType.WIN_BACK, template);
        InstagramBotConfig config = activeConfig();
        InstagramSubscriber inactiveSubscriber = subscriber("inactive-igsid", "Carol");
        inactiveSubscriber.setLastInteractionAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));

        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.WIN_BACK))
                .thenReturn(List.of(rule));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.of(config));
        when(subscriberRepository.findInactiveSince(eq(TENANT), any()))
                .thenReturn(List.of(inactiveSubscriber));
        when(apiClient.sendMessage(eq(config), eq("inactive-igsid"), any())).thenReturn(InstagramSendResult.ok());

        scheduler.processWinBackAutomation();

        verify(apiClient).sendMessage(eq(config), eq("inactive-igsid"),
                eq("We miss you, Carol! Come back for 15% off."));
        verify(messageLogger).record(eq(config), eq("inactive-igsid"), eq(inactiveSubscriber),
                eq(InstagramMessageType.AUTOMATION), any(), argThat(InstagramSendResult::delivered), isNull());
        assertThat(rule.getSentCount()).isEqualTo(1);
        verify(automationRuleRepository).save(rule);
    }

    @Test
    @DisplayName("win-back: default lookback is 14 days when the rule sets no conditions.days_inactive")
    void winBack_defaultsToFourteenDaysWhenConditionsAbsent() {
        InstagramTemplate template = template("We miss you {name}!");
        InstagramAutomationRule rule = rule(InstagramTriggerType.WIN_BACK, template); // no conditions set
        InstagramBotConfig config = activeConfig();

        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.WIN_BACK))
                .thenReturn(List.of(rule));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.of(config));
        when(subscriberRepository.findInactiveSince(eq(TENANT), any())).thenReturn(List.of());

        scheduler.processWinBackAutomation();

        ArgumentCaptor<OffsetDateTime> beforeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(subscriberRepository).findInactiveSince(eq(TENANT), beforeCaptor.capture());
        OffsetDateTime expectedDefault = OffsetDateTime.now(ZoneOffset.UTC).minusDays(14);
        assertThat(beforeCaptor.getValue()).isCloseTo(expectedDefault, within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("win-back: conditions.days_inactive overrides the default lookback")
    void winBack_honoursConditionsDaysInactiveOverride() {
        InstagramTemplate template = template("We miss you {name}!");
        InstagramAutomationRule rule = rule(InstagramTriggerType.WIN_BACK, template);
        rule.setConditions(Map.of("days_inactive", 30));
        InstagramBotConfig config = activeConfig();

        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.WIN_BACK))
                .thenReturn(List.of(rule));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.of(config));
        when(subscriberRepository.findInactiveSince(eq(TENANT), any())).thenReturn(List.of());

        scheduler.processWinBackAutomation();

        ArgumentCaptor<OffsetDateTime> beforeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(subscriberRepository).findInactiveSince(eq(TENANT), beforeCaptor.capture());
        OffsetDateTime expected = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        assertThat(beforeCaptor.getValue()).isCloseTo(expected, within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("no active WIN_BACK rules: nothing is queried or sent")
    void noWinBackRules_noWork() {
        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.WIN_BACK))
                .thenReturn(List.of());

        scheduler.processWinBackAutomation();

        verify(botConfigRepository, never()).findByRestaurantIdAndIsActiveTrue(any());
        verify(subscriberRepository, never()).findInactiveSince(any(), any());
        verify(apiClient, never()).sendMessage(any(), any(), any());
    }

    // -------------------------------------------------------------------------------------------
    // Template quick-reply buttons. The whole point of a button on an automated message is that it is
    // actionable — an "Order now" chip whose payload is ORDER drops the customer straight into the
    // in-DM ordering flow. buttonsConfig was stored but never sent before this, so these pin that the
    // scheduler actually reaches for the quick-reply send path, and only when the flag says to.
    // -------------------------------------------------------------------------------------------

    private InstagramTemplate templateWithButtons(String text, boolean hasButtons,
                                                  List<Map<String, String>> buttons) {
        InstagramTemplate t = template(text);
        t.setHasButtons(hasButtons);
        t.setButtonsConfig(buttons);
        return t;
    }

    private void runBirthdayFor(InstagramTemplate template, InstagramSubscriber subscriber,
                                InstagramBotConfig config) {
        when(automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY))
                .thenReturn(List.of(rule(InstagramTriggerType.BIRTHDAY, template)));
        when(botConfigRepository.findByRestaurantIdAndIsActiveTrue(TENANT)).thenReturn(Optional.of(config));
        when(subscriberRepository.findBirthdaysToday(eq(TENANT), anyInt(), anyInt()))
                .thenReturn(List.of(subscriber));
        scheduler.processBirthdayAutomation();
    }

    @Test
    @DisplayName("a template with buttons is sent through the quick-reply path, carrying the buttons")
    void templateWithButtons_sendsQuickReplies() {
        List<Map<String, String>> buttons = List.of(Map.of("title", "Buyurtma berish", "payload", "ORDER"));
        InstagramTemplate template = templateWithButtons("Happy birthday, {name}!", true, buttons);
        InstagramBotConfig config = activeConfig();
        InstagramSubscriber s = subscriber("bday-igsid", "Alice");
        when(apiClient.sendMessageWithQuickReplies(eq(config), eq("bday-igsid"), any(), any()))
                .thenReturn(InstagramSendResult.ok());

        runBirthdayFor(template, s, config);

        ArgumentCaptor<List<Map<String, String>>> buttonCaptor = ArgumentCaptor.forClass(List.class);
        verify(apiClient).sendMessageWithQuickReplies(
                eq(config), eq("bday-igsid"), eq("Happy birthday, Alice!"), buttonCaptor.capture());
        assertThat(buttonCaptor.getValue()).containsExactlyElementsOf(buttons);
        // The plain-text path must NOT also fire — that would double-send.
        verify(apiClient, never()).sendMessage(any(), any(), any());
        // Still logged as AUTOMATION with the rendered text, exactly like a plain send.
        verify(messageLogger).record(eq(config), eq("bday-igsid"), eq(s),
                eq(InstagramMessageType.AUTOMATION), eq("Happy birthday, Alice!"),
                argThat(InstagramSendResult::delivered), isNull());
    }

    @Test
    @DisplayName("hasButtons=false sends as plain text even when buttonsConfig is populated")
    void buttonsFlagOff_sendsPlainText() {
        InstagramTemplate template = templateWithButtons("Happy birthday, {name}!", false,
                List.of(Map.of("title", "Order", "payload", "ORDER")));
        InstagramBotConfig config = activeConfig();
        when(apiClient.sendMessage(eq(config), eq("bday-igsid"), any())).thenReturn(InstagramSendResult.ok());

        runBirthdayFor(template, subscriber("bday-igsid", "Alice"), config);

        verify(apiClient).sendMessage(eq(config), eq("bday-igsid"), eq("Happy birthday, Alice!"));
        verify(apiClient, never()).sendMessageWithQuickReplies(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a template with no buttons keeps using the plain-text send path")
    void noButtons_sendsPlainText() {
        InstagramTemplate template = templateWithButtons("Happy birthday, {name}!", true, null);
        InstagramBotConfig config = activeConfig();
        when(apiClient.sendMessage(eq(config), eq("bday-igsid"), any())).thenReturn(InstagramSendResult.ok());

        runBirthdayFor(template, subscriber("bday-igsid", "Alice"), config);

        verify(apiClient).sendMessage(eq(config), eq("bday-igsid"), eq("Happy birthday, Alice!"));
        verify(apiClient, never()).sendMessageWithQuickReplies(any(), any(), any(), any());
    }
}
