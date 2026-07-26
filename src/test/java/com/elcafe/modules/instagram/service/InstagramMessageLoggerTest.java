package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramLog;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramInboundMessageRepository;
import com.elcafe.modules.instagram.repository.InstagramLogRepository;
import com.elcafe.modules.sms.enums.MessageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InstagramMessageLogger} must never let a logging failure escape to a caller: a message has
 * already gone out (or definitively failed) by the time {@code record} runs, so a DB hiccup while
 * writing the audit row must not turn a real send into an apparent exception, nor abort a campaign's
 * send loop.
 *
 * <p>Also covers the V175 token-health side effect: {@code record} is the one chokepoint that sees
 * every send's {@link InstagramSendResult}, so it is where a Meta code-190 (invalid/expired token)
 * flips {@code InstagramBotConfig.tokenHealthy} false — see the "Token health" section below.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramMessageLoggerTest {

    private static final Long RESTAURANT = 7L;

    @Mock private InstagramLogRepository logRepository;
    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramInboundMessageRepository inboundMessageRepository;

    @InjectMocks private InstagramMessageLogger logger;

    private final InstagramBotConfig config = InstagramBotConfig.builder().restaurantId(RESTAURANT).build();

    @Test
    @DisplayName("a delivered result saves a SENT row with no error detail")
    void deliveredResult_savesSentRow() {
        InstagramSubscriber subscriber = InstagramSubscriber.builder()
                .id(5L).restaurantId(RESTAURANT).igsid("ig1").build();
        InstagramSendResult result = InstagramSendResult.ok();

        logger.record(config, "ig1", subscriber, InstagramMessageType.MANUAL, "hello", result, null);

        ArgumentCaptor<InstagramLog> captor = ArgumentCaptor.forClass(InstagramLog.class);
        verify(logRepository).save(captor.capture());
        InstagramLog saved = captor.getValue();
        assertThat(saved.getRestaurantId()).isEqualTo(RESTAURANT);
        assertThat(saved.getSubscriber()).isEqualTo(subscriber);
        assertThat(saved.getIgsid()).isEqualTo("ig1");
        assertThat(saved.getMessageType()).isEqualTo(InstagramMessageType.MANUAL);
        assertThat(saved.getMessage()).isEqualTo("hello");
        assertThat(saved.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getErrorCode()).isNull();
    }

    @Test
    @DisplayName("a failed result saves a FAILED row carrying Meta's error message, code and campaign id")
    void failedResult_savesFailedRowWithError() {
        InstagramSendResult result = InstagramSendResult.failed(
                InstagramSendResult.Failure.RECIPIENT_UNAVAILABLE, 551, "blocked by user");

        logger.record(config, "ig2", null, InstagramMessageType.CAMPAIGN, "promo", result, 42L);

        ArgumentCaptor<InstagramLog> captor = ArgumentCaptor.forClass(InstagramLog.class);
        verify(logRepository).save(captor.capture());
        InstagramLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo("blocked by user");
        assertThat(saved.getErrorCode()).isEqualTo(551);
        assertThat(saved.getCampaignId()).isEqualTo(42L);
        assertThat(saved.getSubscriber()).isNull();
    }

    @Test
    @DisplayName("a repository.save exception is swallowed, never thrown to the caller")
    void repositorySaveException_isSwallowed() {
        when(logRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> logger.record(config, "ig3", null, InstagramMessageType.AUTOMATION,
                "hi", InstagramSendResult.ok(), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a null config is skipped (nothing to attribute the row to) and never throws")
    void nullConfig_isSkippedWithoutThrowing() {
        assertThatCode(() -> logger.record(null, "ig4", null, InstagramMessageType.MANUAL,
                "hi", InstagramSendResult.ok(), null))
                .doesNotThrowAnyException();

        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("a null result (e.g. an unstubbed test double) is skipped and never throws")
    void nullResult_isSkippedWithoutThrowing() {
        assertThatCode(() -> logger.record(config, "ig5", null, InstagramMessageType.MANUAL,
                "hi", null, null))
                .doesNotThrowAnyException();

        verify(logRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- Token health (V175)

    @Test
    @DisplayName("a TOKEN_INVALID failure (Meta code 190) flips a healthy config's tokenHealthy to false")
    void tokenInvalidFailure_marksConfigUnhealthy() {
        InstagramBotConfig liveConfig = InstagramBotConfig.builder()
                .id(21L).restaurantId(RESTAURANT).tokenHealthy(true).build();
        InstagramSendResult result = InstagramSendResult.failed(
                InstagramSendResult.Failure.TOKEN_INVALID, 190, "Error validating access token");

        logger.record(liveConfig, "ig6", null, InstagramMessageType.MANUAL, "hi", result, null);

        assertThat(liveConfig.getTokenHealthy()).isFalse();
        verify(configRepository).save(liveConfig);
    }

    @Test
    @DisplayName("a repeated TOKEN_INVALID failure on an already-unhealthy config saves exactly once (idempotent)")
    void repeatedTokenInvalidFailure_savesExactlyOnce() {
        InstagramBotConfig liveConfig = InstagramBotConfig.builder()
                .id(22L).restaurantId(RESTAURANT).tokenHealthy(true).build();
        InstagramSendResult result = InstagramSendResult.failed(
                InstagramSendResult.Failure.TOKEN_INVALID, 190, "Error validating access token");

        // Same config reused across three failed sends in a row (the realistic shape once a token dies:
        // every subsequent send fails identically) — only the FIRST should reach the repository.
        logger.record(liveConfig, "ig7", null, InstagramMessageType.MANUAL, "hi", result, null);
        logger.record(liveConfig, "ig7", null, InstagramMessageType.MANUAL, "hi", result, null);
        logger.record(liveConfig, "ig7", null, InstagramMessageType.MANUAL, "hi", result, null);

        assertThat(liveConfig.getTokenHealthy()).isFalse();
        verify(configRepository, times(1)).save(liveConfig);
    }

    @Test
    @DisplayName("a delivered result never touches token health")
    void deliveredResult_neverTouchesTokenHealth() {
        InstagramBotConfig liveConfig = InstagramBotConfig.builder()
                .id(23L).restaurantId(RESTAURANT).tokenHealthy(true).build();

        logger.record(liveConfig, "ig8", null, InstagramMessageType.MANUAL, "hi", InstagramSendResult.ok(), null);

        assertThat(liveConfig.getTokenHealthy()).isTrue();
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("a non-190 failure (e.g. recipient unavailable) never touches token health")
    void nonTokenFailure_neverTouchesTokenHealth() {
        InstagramBotConfig liveConfig = InstagramBotConfig.builder()
                .id(24L).restaurantId(RESTAURANT).tokenHealthy(true).build();
        InstagramSendResult result = InstagramSendResult.failed(
                InstagramSendResult.Failure.RECIPIENT_UNAVAILABLE, 551, "blocked by user");

        logger.record(liveConfig, "ig9", null, InstagramMessageType.MANUAL, "hi", result, null);

        assertThat(liveConfig.getTokenHealthy()).isTrue();
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("a configRepository failure while marking the token unhealthy is swallowed — log row still saved, never throws")
    void healthUpdateFailure_isSwallowed_logRowStillSaved() {
        InstagramBotConfig liveConfig = InstagramBotConfig.builder()
                .id(25L).restaurantId(RESTAURANT).tokenHealthy(true).build();
        InstagramSendResult result = InstagramSendResult.failed(
                InstagramSendResult.Failure.TOKEN_INVALID, 190, "Error validating access token");
        when(configRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> logger.record(liveConfig, "ig10", null, InstagramMessageType.MANUAL,
                "hi", result, null))
                .doesNotThrowAnyException();

        verify(logRepository).save(any());   // the audit row itself still got written despite the failure
    }

    // ---------------------------------------------------------------- PII erasure

    @Test
    @DisplayName("eraseSubscriberLogs erases by igsid AND by subscriber association (reaches null-subscriber rows)")
    void eraseSubscriberLogs_erasesByIgsidAndSubscriber() {
        InstagramSubscriber subscriber = InstagramSubscriber.builder()
                .id(9L).restaurantId(RESTAURANT).igsid("ig-erase").build();

        logger.eraseSubscriberLogs(subscriber);

        // The igsid delete is what reaches the wizard/campaign/auto-reply rows logged with a null subscriber.
        verify(logRepository).deleteByRestaurantIdAndIgsid(RESTAURANT, "ig-erase");
        verify(logRepository).deleteBySubscriber(subscriber);
    }

    @Test
    @DisplayName("eraseSubscriberLogs with a null-igsid subscriber still erases by association, no NPE")
    void eraseSubscriberLogs_nullIgsid_stillErasesBySubscriber() {
        InstagramSubscriber subscriber = InstagramSubscriber.builder()
                .id(9L).restaurantId(RESTAURANT).igsid(null).build();

        assertThatCode(() -> logger.eraseSubscriberLogs(subscriber)).doesNotThrowAnyException();

        verify(logRepository, never()).deleteByRestaurantIdAndIgsid(any(), any());
        verify(logRepository).deleteBySubscriber(subscriber);
    }

    @Test
    @DisplayName("eraseSubscriberLogs(null) is a no-op that never touches the repository")
    void eraseSubscriberLogs_null_isNoOp() {
        assertThatCode(() -> logger.eraseSubscriberLogs(null)).doesNotThrowAnyException();

        verify(logRepository, never()).deleteBySubscriber(any());
        verify(logRepository, never()).deleteByRestaurantIdAndIgsid(any(), any());
    }

    // ---------------------------------------------------------------- PII erasure: V179 inbound rows
    //
    // instagram_inbound_message (V179) is a second table carrying a subscriber's PII — their own
    // inbound words, not just the outbound instagram_logs rows above — so eraseSubscriberLogs (the ONE
    // call site InstagramBotService#eraseSubscriber and its CustomerDeletedEvent listener already make)
    // must reach it too, by the identical by-igsid / by-association pair.

    @Test
    @DisplayName("eraseSubscriberLogs ALSO erases inbound messages by igsid AND by subscriber association")
    void eraseSubscriberLogs_alsoErasesInboundMessagesByIgsidAndSubscriber() {
        InstagramSubscriber subscriber = InstagramSubscriber.builder()
                .id(9L).restaurantId(RESTAURANT).igsid("ig-erase").build();

        logger.eraseSubscriberLogs(subscriber);

        // Reaches inbound rows recorded before this subscriber existed (a stranger's first message was
        // a STOP/SUBSCRIBE keyword — InstagramWebhookService never links those to a subscriber).
        verify(inboundMessageRepository).deleteByRestaurantIdAndIgsid(RESTAURANT, "ig-erase");
        verify(inboundMessageRepository).deleteBySubscriber(subscriber);
    }

    @Test
    @DisplayName("eraseSubscriberLogs with a null-igsid subscriber still erases inbound rows by association, no NPE")
    void eraseSubscriberLogs_nullIgsid_stillErasesInboundRowsBySubscriber() {
        InstagramSubscriber subscriber = InstagramSubscriber.builder()
                .id(9L).restaurantId(RESTAURANT).igsid(null).build();

        assertThatCode(() -> logger.eraseSubscriberLogs(subscriber)).doesNotThrowAnyException();

        verify(inboundMessageRepository, never()).deleteByRestaurantIdAndIgsid(any(), any());
        verify(inboundMessageRepository).deleteBySubscriber(subscriber);
    }

    @Test
    @DisplayName("eraseSubscriberLogs(null) never touches the inbound-message repository either")
    void eraseSubscriberLogs_null_neverTouchesInboundMessages() {
        assertThatCode(() -> logger.eraseSubscriberLogs(null)).doesNotThrowAnyException();

        verify(inboundMessageRepository, never()).deleteBySubscriber(any());
        verify(inboundMessageRepository, never()).deleteByRestaurantIdAndIgsid(any(), any());
    }
}
