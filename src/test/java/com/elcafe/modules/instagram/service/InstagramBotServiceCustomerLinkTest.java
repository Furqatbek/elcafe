package com.elcafe.modules.instagram.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Subscriber → customer linking: completeRegistration's phone match, and the admin link/unlink escape
 * hatch. Both added by the linking-fix task — Wave 2 order/reservation DMs only reach a subscriber that
 * is linked to a {@code Customer}, and before this fix that link was made with an EXACT string
 * comparison between {@code subscriber.phone} and {@code Customer.phone}, so a locally-formatted
 * "901234567" or spaced "+998 90 123 45 67" never matched a customer stored as "+998901234567" — and
 * there was no way for an admin to fix a miss by hand.
 *
 * <p>See {@link InstagramBotService#canonicalizePhoneForMatching} / {@link
 * InstagramBotService#findCustomerByPhone} for the auto-link fix, and {@link
 * InstagramBotService#linkSubscriberToCustomer} / {@link InstagramBotService#unlinkSubscriberFromCustomer}
 * for the manual controls.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotServiceCustomerLinkTest {

    private static final Long RESTAURANT = 3L;
    private static final Long OTHER_RESTAURANT = 4L;
    private static final String IGSID = "igsid-1";
    private static final String STORED_E164 = "+998901234567";

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramSubscriberAddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;
    // Unstubbed on purpose: findByRestaurant_IdAndDayOfWeek defaults to Optional.empty() ("unknown
    // hours"), so the away-note feature stays silent and none of these replies are affected.
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

    private Customer customer(Long id, Long restaurantId, String phone) {
        return Customer.builder().id(id).restaurantId(restaurantId).phone(phone).build();
    }

    private Customer customer(Long id, String phone) {
        return customer(id, RESTAURANT, phone);
    }

    // -------------------------------------------------------------------------
    // Auto-link on wizard completion — completeRegistration's phone match (via findCustomerByPhone)
    // -------------------------------------------------------------------------

    /** A subscriber one "yo'q" away from completeRegistration, with the given wizard-collected phone. */
    private InstagramSubscriber subscriberAwaitingCompletion(String phone) {
        InstagramSubscriber s = InstagramSubscriber.builder()
                .id(1L).restaurantId(RESTAURANT).igsid(IGSID)
                .displayName("Aziz").phone(phone)
                .conversationState("AWAITING_MORE_ADDRESSES").isActive(true).isBlocked(false)
                .build();
        when(subscriberRepository.findByIgsidAndRestaurantId(IGSID, RESTAURANT))
                .thenReturn(Optional.of(s));
        return s;
    }

    private void complete() {
        service.handleIncomingMessage(config, IGSID, null, InstagramInboundKind.TEXT, "yo'q", null);
    }

    @Test
    @DisplayName("a local-format phone (\"901234567\") links to a customer stored as E.164")
    void localFormatLinksToStoredE164() {
        InstagramSubscriber s = subscriberAwaitingCompletion("901234567");
        Customer c = customer(10L, STORED_E164);
        when(customerRepository.findByPhoneAndRestaurantId(STORED_E164, RESTAURANT))
                .thenReturn(Optional.of(c));

        complete();

        assertThat(s.getCustomer()).isEqualTo(c);
        assertThat(s.getConversationState()).isEqualTo("REGISTERED");
    }

    @Test
    @DisplayName("a spaced phone (\"+998 90 123 45 67\") links to the same stored customer")
    void spacedPhoneLinksToStoredE164() {
        InstagramSubscriber s = subscriberAwaitingCompletion("+998 90 123 45 67");
        Customer c = customer(10L, STORED_E164);
        when(customerRepository.findByPhoneAndRestaurantId(STORED_E164, RESTAURANT))
                .thenReturn(Optional.of(c));

        complete();

        assertThat(s.getCustomer()).isEqualTo(c);
    }

    @Test
    @DisplayName("an already-E.164 phone still links (regression check on the pre-existing case)")
    void alreadyE164StillLinks() {
        InstagramSubscriber s = subscriberAwaitingCompletion(STORED_E164);
        Customer c = customer(10L, STORED_E164);
        when(customerRepository.findByPhoneAndRestaurantId(STORED_E164, RESTAURANT))
                .thenReturn(Optional.of(c));

        complete();

        assertThat(s.getCustomer()).isEqualTo(c);
    }

    @Test
    @DisplayName("falls back to a canonical scan when Customer.phone is stored without a leading '+'")
    void fallsBackToCanonicalScanForDifferentlyStoredCustomer() {
        InstagramSubscriber s = subscriberAwaitingCompletion(STORED_E164);
        // Customer.phone stored "998901234567" (no leading +, e.g. via ConsumerAuthService's
        // digits-and-plus-only normalizer never adding one) — the exact-match branch misses it.
        Customer c = customer(10L, "998901234567");
        when(customerRepository.findByPhoneAndRestaurantId(STORED_E164, RESTAURANT))
                .thenReturn(Optional.empty());
        when(customerRepository.findByPhoneContainingAndRestaurantId("901234567", RESTAURANT))
                .thenReturn(List.of(c));

        complete();

        assertThat(s.getCustomer()).isEqualTo(c);
    }

    @Test
    @DisplayName("a non-matching phone links nothing, but registration still completes")
    void nonMatchingPhoneLinksNothing() {
        InstagramSubscriber s = subscriberAwaitingCompletion("+998907654321");
        when(customerRepository.findByPhoneAndRestaurantId(anyString(), eq(RESTAURANT)))
                .thenReturn(Optional.empty());
        when(customerRepository.findByPhoneContainingAndRestaurantId(anyString(), eq(RESTAURANT)))
                .thenReturn(List.of());

        complete();

        assertThat(s.getCustomer()).isNull();
        assertThat(s.getConversationState()).isEqualTo("REGISTERED");
    }

    @Test
    @DisplayName("two customers sharing a canonical phone link deterministically to the lowest id")
    void ambiguousMatchPicksLowestId() {
        InstagramSubscriber s = subscriberAwaitingCompletion(STORED_E164);
        Customer higherId = customer(9L, "998901234567");
        Customer lowerId = customer(2L, "+998 90 123 45 67");
        when(customerRepository.findByPhoneAndRestaurantId(STORED_E164, RESTAURANT))
                .thenReturn(Optional.empty());
        // Deliberately returned in an order that does NOT already put the lowest id first, so the
        // assertion only passes if the service itself sorts rather than trusting query order.
        when(customerRepository.findByPhoneContainingAndRestaurantId("901234567", RESTAURANT))
                .thenReturn(List.of(higherId, lowerId));

        complete();

        assertThat(s.getCustomer()).isEqualTo(lowerId);
    }

    // -------------------------------------------------------------------------
    // Admin link/unlink — manual escape hatch, tenant-scoped
    // -------------------------------------------------------------------------

    private InstagramSubscriber existingSubscriber(Long id, Long restaurantId) {
        return InstagramSubscriber.builder()
                .id(id).restaurantId(restaurantId).igsid("ig-" + id)
                .displayName("Aziz").phone(STORED_E164)
                .conversationState("REGISTERED").isActive(true).isBlocked(false)
                .build();
    }

    @Test
    @DisplayName("admin link: sets the customer when both belong to the same restaurant")
    void adminLinkSucceedsForSameRestaurant() {
        InstagramSubscriber s = existingSubscriber(1L, RESTAURANT);
        Customer c = customer(10L, STORED_E164);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(1L, RESTAURANT)).thenReturn(Optional.of(s));
        when(customerRepository.findById(10L)).thenReturn(Optional.of(c));

        InstagramSubscriber result = service.linkSubscriberToCustomer(1L, 10L);

        assertThat(result.getCustomer()).isEqualTo(c);
        verify(subscriberRepository).save(s);
    }

    @Test
    @DisplayName("admin link: a customer from a different restaurant is rejected, nothing is saved")
    void adminLinkRejectsForeignCustomer() {
        InstagramSubscriber s = existingSubscriber(1L, RESTAURANT);
        Customer foreignCustomer = customer(99L, OTHER_RESTAURANT, STORED_E164);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(1L, RESTAURANT)).thenReturn(Optional.of(s));
        when(customerRepository.findById(99L)).thenReturn(Optional.of(foreignCustomer));

        assertThatThrownBy(() -> service.linkSubscriberToCustomer(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).save(any());
        assertThat(s.getCustomer()).isNull();
    }

    @Test
    @DisplayName("admin link: a foreign subscriber id is rejected, no customer lookup happens")
    void adminLinkRejectsForeignSubscriber() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(77L, RESTAURANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkSubscriberToCustomer(77L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(customerRepository, never()).findById(any());
        verify(subscriberRepository, never()).save(any());
    }

    @Test
    @DisplayName("admin link: a nonexistent customer id is rejected the same as a foreign one")
    void adminLinkRejectsUnknownCustomer() {
        InstagramSubscriber s = existingSubscriber(1L, RESTAURANT);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(1L, RESTAURANT)).thenReturn(Optional.of(s));
        when(customerRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkSubscriberToCustomer(1L, 404L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).save(any());
    }

    @Test
    @DisplayName("admin link: a missing customerId is a bad request")
    void adminLinkRequiresCustomerId() {
        InstagramSubscriber s = existingSubscriber(1L, RESTAURANT);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(1L, RESTAURANT)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.linkSubscriberToCustomer(1L, null))
                .isInstanceOf(BadRequestException.class);
        verify(subscriberRepository, never()).save(any());
    }

    @Test
    @DisplayName("admin unlink: clears an existing customer link")
    void adminUnlinkClearsCustomer() {
        InstagramSubscriber s = existingSubscriber(1L, RESTAURANT);
        s.setCustomer(customer(10L, STORED_E164));
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(1L, RESTAURANT)).thenReturn(Optional.of(s));

        InstagramSubscriber result = service.unlinkSubscriberFromCustomer(1L);

        assertThat(result.getCustomer()).isNull();
        verify(subscriberRepository).save(s);
    }

    @Test
    @DisplayName("admin unlink: a foreign subscriber id is rejected, nothing is saved")
    void adminUnlinkRejectsForeignSubscriber() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(77L, RESTAURANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlinkSubscriberFromCustomer(77L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // canonicalizePhoneForMatching — the pure function directly, for format edge cases that do not
    // each need their own end-to-end wizard scenario above.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("canonicalizePhoneForMatching: local, spaced, dashed, 0-trunk and E.164 forms all converge")
    void canonicalizeConvergesOnCommonForms() {
        assertThat(InstagramBotService.canonicalizePhoneForMatching("901234567")).isEqualTo(STORED_E164);
        assertThat(InstagramBotService.canonicalizePhoneForMatching("+998 90 123 45 67")).isEqualTo(STORED_E164);
        assertThat(InstagramBotService.canonicalizePhoneForMatching("+998901234567")).isEqualTo(STORED_E164);
        assertThat(InstagramBotService.canonicalizePhoneForMatching("998901234567")).isEqualTo(STORED_E164);
        assertThat(InstagramBotService.canonicalizePhoneForMatching("0901234567")).isEqualTo(STORED_E164);
        assertThat(InstagramBotService.canonicalizePhoneForMatching("90-123-45-67")).isEqualTo(STORED_E164);
        assertThat(InstagramBotService.canonicalizePhoneForMatching("(90) 123 45 67")).isEqualTo(STORED_E164);
    }

    @Test
    @DisplayName("canonicalizePhoneForMatching: null/blank input never throws")
    void canonicalizeHandlesNullAndBlank() {
        assertThat(InstagramBotService.canonicalizePhoneForMatching(null)).isEqualTo("");
        assertThat(InstagramBotService.canonicalizePhoneForMatching("")).isEqualTo("");
        assertThat(InstagramBotService.canonicalizePhoneForMatching("   ")).isEqualTo("");
    }
}
