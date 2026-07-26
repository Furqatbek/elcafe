package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramLog;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InstagramMessageLogger} must never let a logging failure escape to a caller: a message has
 * already gone out (or definitively failed) by the time {@code record} runs, so a DB hiccup while
 * writing the audit row must not turn a real send into an apparent exception, nor abort a campaign's
 * send loop.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramMessageLoggerTest {

    private static final Long RESTAURANT = 7L;

    @Mock private InstagramLogRepository logRepository;

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
}
