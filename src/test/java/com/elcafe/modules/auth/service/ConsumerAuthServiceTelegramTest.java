package com.elcafe.modules.auth.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.UnauthorizedException;
import com.elcafe.modules.auth.dto.TelegramMiniAppAuthRequest;
import com.elcafe.modules.auth.dto.TelegramMiniAppAuthResponse;
import com.elcafe.modules.auth.repository.ConsumerSessionRepository;
import com.elcafe.modules.auth.repository.OtpCodeRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.enums.RegistrationSource;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.sms.service.SmsService;
import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.entity.TelegramSubscriberLocation;
import com.elcafe.modules.telegram.repository.TelegramBotConfigRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberLocationRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import com.elcafe.modules.telegram.service.TelegramInitDataValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the Telegram Mini App login branch of {@link ConsumerAuthService}. The signature
 * verification itself is proven in {@link TelegramInitDataValidator}'s own test and mocked here, so these
 * tests focus on the orchestration: no-bot, invalid payload, the "share your contact first" gate, and the
 * two happy paths (existing vs newly-created customer) that must mint a real consumer session.
 */
@ExtendWith(MockitoExtension.class)
class ConsumerAuthServiceTelegramTest {

    private static final Long RESTAURANT_ID = 10L;
    private static final Long TELEGRAM_USER_ID = 42L;

    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private ConsumerSessionRepository sessionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private LoyaltyService loyaltyService;
    @Mock private SmsService smsService;
    @Mock private TelegramBotConfigRepository telegramBotConfigRepository;
    @Mock private TelegramSubscriberRepository telegramSubscriberRepository;
    @Mock private TelegramSubscriberLocationRepository telegramSubscriberLocationRepository;
    @Mock private TelegramInitDataValidator telegramInitDataValidator;

    @InjectMocks private ConsumerAuthService service;

    @BeforeEach
    void setUp() {
        // @Value fields Mockito can't inject — only those the Telegram path actually reads.
        ReflectionTestUtils.setField(service, "jwtSecret", "test-jwt-secret-key-please-be-32-bytes-or-more!!");
        ReflectionTestUtils.setField(service, "accessTokenExpiration", 3_600_000L);
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 2_592_000_000L);
    }

    private TelegramMiniAppAuthRequest request() {
        return TelegramMiniAppAuthRequest.builder()
                .restaurantId(RESTAURANT_ID)
                .initData("user=%7B%22id%22%3A42%7D&auth_date=1&hash=deadbeef")
                .build();
    }

    private TelegramBotConfig activeBot() {
        return TelegramBotConfig.builder()
                .restaurantId(RESTAURANT_ID).botToken("123:token").botUsername("qahvoon_bot").isActive(true).build();
    }

    private TelegramInitDataValidator.ValidatedTelegramUser validatedUser() {
        return new TelegramInitDataValidator.ValidatedTelegramUser(
                TELEGRAM_USER_ID, "Ali", "Valiyev", "ali_test", "uz", 1L);
    }

    private void botAndSignatureAreValid() {
        when(telegramBotConfigRepository.findByRestaurantIdAndIsActiveTrue(RESTAURANT_ID))
                .thenReturn(Optional.of(activeBot()));
        when(telegramInitDataValidator.validate(anyString(), anyString())).thenReturn(validatedUser());
    }

    @Test
    @DisplayName("no active bot for the restaurant → BadRequestException, signature never checked")
    void noActiveBot_throws() {
        when(telegramBotConfigRepository.findByRestaurantIdAndIsActiveTrue(RESTAURANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticateViaTelegram(request(), "1.1.1.1", "ua"))
                .isInstanceOf(BadRequestException.class);

        verify(telegramInitDataValidator, never()).validate(anyString(), anyString());
    }

    @Test
    @DisplayName("invalid initData signature propagates (401), no customer/session touched")
    void invalidSignature_propagates() {
        when(telegramBotConfigRepository.findByRestaurantIdAndIsActiveTrue(RESTAURANT_ID))
                .thenReturn(Optional.of(activeBot()));
        when(telegramInitDataValidator.validate(anyString(), anyString()))
                .thenThrow(new UnauthorizedException("Telegram initData signature is invalid"));

        assertThatThrownBy(() -> service.authenticateViaTelegram(request(), "1.1.1.1", "ua"))
                .isInstanceOf(UnauthorizedException.class);

        verify(customerRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("verified Telegram identity but no shared phone → registrationRequired, no session issued")
    void noVerifiedPhone_requiresRegistration() {
        botAndSignatureAreValid();
        // First-time Mini App visitor who never completed the bot wizard: no subscriber row yet.
        when(telegramSubscriberRepository.findByTelegramUserIdAndRestaurantId(TELEGRAM_USER_ID, RESTAURANT_ID))
                .thenReturn(Optional.empty());
        when(telegramSubscriberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TelegramMiniAppAuthResponse response = service.authenticateViaTelegram(request(), "1.1.1.1", "ua");

        assertThat(response.isRegistrationRequired()).isTrue();
        assertThat(response.getAuth()).isNull();
        verify(customerRepository, never()).findByPhoneAndRestaurantId(anyString(), anyLong());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("subscriber with a verified phone + existing customer → issues a consumer session")
    void existingCustomer_issuesSession() {
        botAndSignatureAreValid();
        TelegramSubscriber subscriber = TelegramSubscriber.builder()
                .restaurantId(RESTAURANT_ID).telegramUserId(TELEGRAM_USER_ID).phone("+998901234567").build();
        when(telegramSubscriberRepository.findByTelegramUserIdAndRestaurantId(TELEGRAM_USER_ID, RESTAURANT_ID))
                .thenReturn(Optional.of(subscriber));
        when(telegramSubscriberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Customer existing = Customer.builder().id(5L).restaurantId(RESTAURANT_ID)
                .firstName("Ali").phone("+998901234567").build();
        when(customerRepository.findByPhoneAndRestaurantId("+998901234567", RESTAURANT_ID))
                .thenReturn(Optional.of(existing));
        when(loyaltyService.grantRegistrationBonus(5L, RESTAURANT_ID)).thenReturn(BigDecimal.ZERO);
        // A default saved delivery pin should surface as checkout prefill.
        TelegramSubscriberLocation pin = TelegramSubscriberLocation.builder()
                .latitude(41.31).longitude(69.28).isDefault(true).build();
        when(telegramSubscriberLocationRepository.findAllBySubscriber(any())).thenReturn(List.of(pin));

        TelegramMiniAppAuthResponse response = service.authenticateViaTelegram(request(), "1.1.1.1", "ua");

        assertThat(response.isRegistrationRequired()).isFalse();
        assertThat(response.getAuth()).isNotNull();
        assertThat(response.getAuth().getAccessToken()).isNotBlank();
        assertThat(response.getAuth().getRefreshToken()).isNotBlank();
        assertThat(response.getAuth().getCustomerId()).isEqualTo(5L);
        assertThat(response.getPrefill()).isNotNull();
        assertThat(response.getPrefill().getPhone()).isEqualTo("+998901234567");
        assertThat(response.getPrefill().getLatitude()).isEqualTo(41.31);
        assertThat(response.getPrefill().getLongitude()).isEqualTo(69.28);
        verify(sessionRepository).invalidateAllSessionsByCustomerId(5L);
        verify(sessionRepository).save(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("subscriber with a verified phone but no customer yet → creates + links customer, issues session")
    void newCustomer_isCreatedAndLinked() {
        botAndSignatureAreValid();
        TelegramSubscriber subscriber = TelegramSubscriber.builder()
                .restaurantId(RESTAURANT_ID).telegramUserId(TELEGRAM_USER_ID).phone("+998907654321").build();
        when(telegramSubscriberRepository.findByTelegramUserIdAndRestaurantId(TELEGRAM_USER_ID, RESTAURANT_ID))
                .thenReturn(Optional.of(subscriber));
        when(telegramSubscriberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(customerRepository.findByPhoneAndRestaurantId("+998907654321", RESTAURANT_ID))
                .thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(77L);
            return c;
        });
        when(loyaltyService.grantRegistrationBonus(77L, RESTAURANT_ID)).thenReturn(BigDecimal.ZERO);

        TelegramMiniAppAuthResponse response = service.authenticateViaTelegram(request(), "1.1.1.1", "ua");

        assertThat(response.isRegistrationRequired()).isFalse();
        assertThat(response.getAuth().getCustomerId()).isEqualTo(77L);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer created = customerCaptor.getValue();
        assertThat(created.getRestaurantId()).isEqualTo(RESTAURANT_ID);
        assertThat(created.getPhone()).isEqualTo("+998907654321");
        assertThat(created.getRegistrationSource()).isEqualTo(RegistrationSource.TELEGRAM_BOT);
        assertThat(created.getFirstName()).isEqualTo("Ali");

        // The subscriber is linked to the freshly-created customer.
        assertThat(subscriber.getCustomer()).isNotNull();
        assertThat(subscriber.getCustomer().getId()).isEqualTo(77L);
    }
}
