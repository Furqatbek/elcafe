package com.elcafe.modules.referral.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.referral.entity.Referral;
import com.elcafe.modules.referral.entity.ReferralCode;
import com.elcafe.modules.referral.enums.ReferralStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class ReferralRepositoryTest {

    @Autowired private ReferralRepository referralRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Customer referrer;
    private Customer referee1;
    private Customer referee2;
    private ReferralCode referralCode;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().name("Test Cafe").address("123 Main St").build();
        em.persist(restaurant);

        referrer = Customer.builder().firstName("Referrer").lastName("User").phone("+998900000001").active(true).build();
        referee1 = Customer.builder().firstName("Referee1").lastName("User").phone("+998900000002").active(true).build();
        referee2 = Customer.builder().firstName("Referee2").lastName("User").phone("+998900000003").active(true).build();
        em.persist(referrer);
        em.persist(referee1);
        em.persist(referee2);

        referralCode = ReferralCode.builder()
                .restaurant(restaurant).customer(referrer).code("REF001").active(true).build();
        em.persist(referralCode);
    }

    @Test @DisplayName("countByReferrerAndRestaurantAndStatus — filters by referrer, restaurant, and status")
    void countByReferrerAndRestaurantAndStatus() {
        em.persist(Referral.builder().restaurant(restaurant).referralCode(referralCode)
                .referrer(referrer).referee(referee1).status(ReferralStatus.COMPLETED).build());
        em.persist(Referral.builder().restaurant(restaurant).referralCode(referralCode)
                .referrer(referrer).referee(referee2).status(ReferralStatus.PENDING).build());

        em.flush(); em.clear();

        assertEquals(1L, referralRepository.countByReferrerAndRestaurantAndStatus(
                referrer.getId(), restaurant.getId(), ReferralStatus.COMPLETED));
        assertEquals(1L, referralRepository.countByReferrerAndRestaurantAndStatus(
                referrer.getId(), restaurant.getId(), ReferralStatus.PENDING));
        assertEquals(0L, referralRepository.countByReferrerAndRestaurantAndStatus(
                referrer.getId(), restaurant.getId(), ReferralStatus.EXPIRED));
    }

    @Test @DisplayName("findExpiredPendingReferrals — returns PENDING referrals created before expiry date")
    void findExpiredPendingReferrals() {
        Referral expired = Referral.builder().restaurant(restaurant).referralCode(referralCode)
                .referrer(referrer).referee(referee1).status(ReferralStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(60)).build();
        em.persist(expired);

        Referral recent = Referral.builder().restaurant(restaurant).referralCode(referralCode)
                .referrer(referrer).referee(referee2).status(ReferralStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(5)).build();
        em.persist(recent);

        em.flush(); em.clear();

        LocalDateTime expiryDate = LocalDateTime.now().minusDays(30);
        List<Referral> expiredReferrals = referralRepository.findExpiredPendingReferrals(expiryDate);

        assertEquals(1, expiredReferrals.size());
        assertEquals(referee1.getId(), expiredReferrals.get(0).getReferee().getId());
    }

    @Test @DisplayName("countByRestaurantIdAndCreatedAtAfter — counts referrals since a start date")
    void countByRestaurantIdAndCreatedAtAfter() {
        em.persist(Referral.builder().restaurant(restaurant).referralCode(referralCode)
                .referrer(referrer).referee(referee1).status(ReferralStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(3)).build());
        em.persist(Referral.builder().restaurant(restaurant).referralCode(referralCode)
                .referrer(referrer).referee(referee2).status(ReferralStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(60)).build());

        em.flush(); em.clear();

        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        long count = referralRepository.countByRestaurantIdAndCreatedAtAfter(restaurant.getId(), startDate);

        assertEquals(1L, count);
    }
}
