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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduler for Instagram automation rules — the ShedLock-guarded daily jobs that fulfil the
 * "🎁 you'll get birthday gifts" promise the registration wizard already makes but which, until this
 * class, the system never kept. Mirrors {@code TelegramScheduler}'s birthday/inactive-user jobs (same
 * 9:00/10:00 daily cron slots, same ShedLock discipline), adapted to Instagram's per-tenant model and
 * its own typed {@link InstagramSendResult} — and, unlike Telegram, resolving a subscriber's OWN
 * {@code birthDate} directly rather than a linked {@code Customer}'s, since the wizard already collects
 * it on {@link InstagramSubscriber} itself (V163).
 *
 * <p><b>IMPORTANT — read this before assuming a low sent-count means a bug.</b> Unlike Telegram
 * (long-polling / a webhook with no send-window restriction), Meta allows an Instagram DM ONLY within
 * 24 hours of the recipient's last INBOUND message to the business, and — critically — marketing and
 * automated content is not eligible for any of Meta's window-extending message tags (those exist for
 * things like post-purchase order updates, not birthday greetings or re-engagement pushes). Concretely:
 * <ul>
 *   <li><b>BIRTHDAY</b>: a subscriber whose birthday is today but who has not messaged the business in
 *       the last 24h will have their greeting REJECTED by Meta —
 *       {@link InstagramSendResult.Failure#RECIPIENT_UNAVAILABLE} (error code 10, or subcode 2534014
 *       "outside the allowed window").</li>
 *   <li><b>WIN_BACK</b>: this is even more fundamental — the entire audience is, BY DEFINITION, people
 *       who have gone quiet, so most of them are almost certainly outside the 24h window on any given
 *       day. A win-back push on Instagram largely cannot reach the very people it targets; SMS/push/
 *       email remain the reliable channels for a truly lapsed customer.</li>
 * </ul>
 * This is an INHERENT Meta platform constraint, not a bug in this scheduler or in
 * {@link InstagramApiClient}. Both jobs below still ATTEMPT and LOG every eligible subscriber every day
 * — an in-window subscriber (recently active, or who simply happens to message the business around
 * their birthday) genuinely IS reached, and every attempt (delivered or rejected) leaves an auditable
 * {@code InstagramLog} row via {@link InstagramMessageLogger} — rather than trying to pre-guess who is
 * in-window and silently skipping the rest, which would hide the very rejections an operator needs to
 * see in order to understand this limitation. Contrast {@code InstagramCampaignService}'s manual
 * broadcast path, which DOES pre-filter its audience to the in-window subset
 * ({@code InstagramSubscriberRepository#findAllActiveNotBlockedSince}) — that is a human explicitly
 * choosing to send right now; a job that fires once a day, unattended, has no equivalent "now" signal
 * to filter against, and BIRTHDAY's whole point is reaching a subscriber ON their birthday, window or
 * not.
 *
 * <p><b>Transaction boundaries, deliberately.</b> Unlike {@code TelegramScheduler} (which wraps each
 * whole job in one {@code @Transactional} method), neither job here carries a class- or method-level
 * {@code @Transactional}: {@link InstagramMessageLogger#record} is documented to always open its OWN
 * independent transaction on the assumption that its caller has NO ambient one open, and a single
 * {@code @Transactional} spanning this scheduler's entire cross-tenant sweep — including one Meta HTTP
 * round trip per subscriber — would both violate that assumption and hold one database transaction open
 * for the duration of potentially many slow external calls across every restaurant. Every repository
 * call below (the rule/subscriber finders, and the final {@code ruleRepository.save}) is transactional
 * on its own via Spring Data JPA's repository proxy, which is the right granularity here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstagramScheduler {

    private final InstagramAutomationRuleRepository automationRuleRepository;
    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramBotConfigRepository botConfigRepository;
    private final InstagramApiClient apiClient;
    private final InstagramMessageLogger messageLogger;

    /** Default WIN_BACK lookback when a rule sets no explicit {@code conditions.days_inactive} — matches TelegramScheduler's own default. */
    private static final int DEFAULT_WIN_BACK_DAYS_INACTIVE = 14;

    /**
     * Process birthday automation every day at 9:00 AM — same slot as {@code TelegramScheduler
     * #processBirthdayAutomation}. Sends to every subscriber, of every restaurant with an active
     * BIRTHDAY rule, whose OWN {@code birthDate} (month + day) is today.
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @SchedulerLock(name = "instagram-birthday-automation", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void processBirthdayAutomation() {
        List<InstagramAutomationRule> rules =
                automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.BIRTHDAY);
        if (rules.isEmpty()) {
            log.debug("No active Instagram birthday automation rules found");
            return;
        }

        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();
        log.info("Processing Instagram birthday automation for {} rule(s)...", rules.size());

        for (InstagramAutomationRule rule : rules) {
            processRule(rule,
                    restaurantId -> subscriberRepository.findBirthdaysToday(restaurantId, month, day),
                    InstagramScheduler::birthdayPlaceholders);
        }
    }

    /**
     * Process win-back automation every day at 10:00 AM — same slot as {@code TelegramScheduler
     * #processInactiveUserAutomation}. Sends to every subscriber, of every restaurant with an active
     * WIN_BACK rule, whose {@code lastInteractionAt} is older than that rule's threshold (default 14
     * days, overridable per-rule via {@code conditions.days_inactive} — same knob and default Telegram's
     * inactive-user job uses).
     */
    @Scheduled(cron = "0 0 10 * * ?")
    @SchedulerLock(name = "instagram-winback-automation", lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void processWinBackAutomation() {
        List<InstagramAutomationRule> rules =
                automationRuleRepository.findActiveRulesWithTemplate(InstagramTriggerType.WIN_BACK);
        if (rules.isEmpty()) {
            log.debug("No active Instagram win-back automation rules found");
            return;
        }
        log.info("Processing Instagram win-back automation for {} rule(s)...", rules.size());

        for (InstagramAutomationRule rule : rules) {
            int daysInactive = winBackDaysInactive(rule);
            OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusDays(daysInactive);

            processRule(rule,
                    restaurantId -> subscriberRepository.findInactiveSince(restaurantId, before),
                    InstagramScheduler::winBackPlaceholders);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Shared send loop — both jobs above differ only in HOW subscribers are found and WHAT
    // placeholders a subscriber renders to; everything else (config resolution, sending, logging,
    // fatal-failure handling, the rule's own sentCount/lastTriggeredAt bump) is identical.
    // -------------------------------------------------------------------------------------------

    @FunctionalInterface
    private interface SubscriberFinder {
        List<InstagramSubscriber> find(Long restaurantId);
    }

    @FunctionalInterface
    private interface PlaceholderBuilder {
        Map<String, String> build(InstagramSubscriber subscriber);
    }

    private void processRule(InstagramAutomationRule rule, SubscriberFinder finder, PlaceholderBuilder placeholders) {
        Long restaurantId = rule.getRestaurantId();
        InstagramTemplate template = rule.getTemplate();
        if (template == null) {
            log.warn("Instagram automation rule {} ({}) has no template — skipped", rule.getId(), rule.getName());
            return;
        }

        // Bound for the duration of this one rule's work so every downstream call (and its log lines)
        // is attributable to the right tenant, and cleared in `finally` so nothing leaks onto whatever
        // task this pooled scheduler thread picks up next — mirroring InstagramCampaignExecutor.
        TenantContext.setRestaurantId(restaurantId);
        try {
            InstagramBotConfig config = botConfigRepository.findByRestaurantIdAndIsActiveTrue(restaurantId).orElse(null);
            if (config == null) {
                log.debug("No active Instagram config for restaurant {} — automation rule {} skipped",
                        restaurantId, rule.getId());
                return;
            }

            List<InstagramSubscriber> targets = finder.find(restaurantId);
            if (targets.isEmpty()) {
                return;
            }
            log.info("Instagram automation rule {} ({}, restaurant {}): {} candidate subscriber(s)",
                    rule.getId(), rule.getName(), restaurantId, targets.size());

            int sentCount = 0;
            for (InstagramSubscriber subscriber : targets) {
                String rendered = template.render(placeholders.build(subscriber));
                InstagramSendResult result = apiClient.sendMessage(config, subscriber.getIgsid(), rendered);
                messageLogger.record(config, subscriber.getIgsid(), subscriber,
                        InstagramMessageType.AUTOMATION, rendered, result, null);

                if (result.delivered()) {
                    sentCount++;
                    log.info("Sent Instagram {} message to subscriber igsid={}", rule.getTriggerType(), subscriber.getIgsid());
                } else if (isFatalForChannel(result.failure())) {
                    // Every remaining subscriber under this SAME config would fail identically (dead
                    // token / throttled / open circuit) — stop THIS rule's run. Other rules (other
                    // restaurants, other tokens) are entirely unaffected, since the outer loop moves on
                    // to the next rule regardless.
                    log.warn("Instagram automation rule {} halted ({}): {} of {} candidate(s) sent",
                            rule.getId(), result.failure(), sentCount, targets.size());
                    break;
                }
                // Any other per-recipient failure (blocked, outside the 24h window — see class javadoc,
                // a transient Meta fault) is already recorded by messageLogger.record above; move on to
                // the next candidate rather than letting one subscriber's rejection stop the rest.
            }

            if (sentCount > 0) {
                rule.setSentCount((rule.getSentCount() != null ? rule.getSentCount() : 0) + sentCount);
                rule.setLastTriggeredAt(OffsetDateTime.now(ZoneOffset.UTC));
                automationRuleRepository.save(rule);
                log.info("Instagram automation rule {} ({}): sent {} of {}",
                        rule.getId(), rule.getName(), sentCount, targets.size());
            }
        } finally {
            TenantContext.clear();
        }
    }

    /** {@code conditions.days_inactive} when present and numeric, else {@link #DEFAULT_WIN_BACK_DAYS_INACTIVE}. */
    private static int winBackDaysInactive(InstagramAutomationRule rule) {
        if (rule.getConditions() != null && rule.getConditions().get("days_inactive") instanceof Number n) {
            return n.intValue();
        }
        return DEFAULT_WIN_BACK_DAYS_INACTIVE;
    }

    private static Map<String, String> birthdayPlaceholders(InstagramSubscriber subscriber) {
        Map<String, String> placeholders = new HashMap<>();
        String name = subscriber.getDisplayNameOrFallback();
        placeholders.put("name", name);
        placeholders.put("first_name", name);
        return placeholders;
    }

    private static Map<String, String> winBackPlaceholders(InstagramSubscriber subscriber) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", subscriber.getDisplayNameOrFallback());
        return placeholders;
    }

    /** Mirrors {@code InstagramCampaignExecutor#isFatal}: failures that doom every other send on this same config. */
    private static boolean isFatalForChannel(InstagramSendResult.Failure failure) {
        return failure == InstagramSendResult.Failure.TOKEN_INVALID
                || failure == InstagramSendResult.Failure.RATE_LIMITED
                || failure == InstagramSendResult.Failure.CIRCUIT_OPEN;
    }
}
