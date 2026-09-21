package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.entity.InstagramSubscriberAddress;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramSubscriberAddressRepositoryTest {

    @Autowired
    private InstagramSubscriberAddressRepository repo;

    @Autowired
    private EntityManager em;

    private InstagramSubscriber subscriber;
    private InstagramSubscriber otherSubscriber;

    @BeforeEach
    void setUp() {
        subscriber = new InstagramSubscriber();
        subscriber.setIgsid("ig-addr-test-1");
        subscriber.setUsername("addruser");
        subscriber.setIsActive(true);
        subscriber.setIsBlocked(false);
        em.persist(subscriber);

        otherSubscriber = new InstagramSubscriber();
        otherSubscriber.setIgsid("ig-addr-test-2");
        otherSubscriber.setUsername("otheruser");
        otherSubscriber.setIsActive(true);
        otherSubscriber.setIsBlocked(false);
        em.persist(otherSubscriber);

        em.flush();
        em.clear();
    }

    private InstagramSubscriberAddress createAddress(InstagramSubscriber sub, String address, boolean isDefault) {
        InstagramSubscriberAddress addr = new InstagramSubscriberAddress();
        addr.setSubscriber(em.find(InstagramSubscriber.class, sub.getId()));
        addr.setAddress(address);
        addr.setIsDefault(isDefault);
        em.persist(addr);
        return addr;
    }

    @Test
    void findAllBySubscriber_returnsAddressesForSubscriber() {
        createAddress(subscriber, "123 Main St", true);
        createAddress(subscriber, "456 Oak Ave", false);
        createAddress(otherSubscriber, "789 Pine Rd", true);
        em.flush();
        em.clear();

        InstagramSubscriber managedSub = em.find(InstagramSubscriber.class, subscriber.getId());
        List<InstagramSubscriberAddress> results = repo.findAllBySubscriber(managedSub);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(a ->
                a.getAddress().equals("123 Main St") || a.getAddress().equals("456 Oak Ave")));
    }

    @Test
    void findAllBySubscriber_returnsEmptyForSubscriberWithNoAddresses() {
        em.flush();
        em.clear();

        InstagramSubscriber managedSub = em.find(InstagramSubscriber.class, subscriber.getId());
        List<InstagramSubscriberAddress> results = repo.findAllBySubscriber(managedSub);

        assertTrue(results.isEmpty());
    }

    @Test
    void countBySubscriber_returnsCorrectCount() {
        createAddress(subscriber, "123 Main St", true);
        createAddress(subscriber, "456 Oak Ave", false);
        createAddress(otherSubscriber, "789 Pine Rd", true);
        em.flush();
        em.clear();

        InstagramSubscriber managedSub = em.find(InstagramSubscriber.class, subscriber.getId());
        long count = repo.countBySubscriber(managedSub);

        assertEquals(2, count);
    }

    @Test
    void countBySubscriber_returnsZeroForSubscriberWithNoAddresses() {
        em.flush();
        em.clear();

        InstagramSubscriber managedSub = em.find(InstagramSubscriber.class, subscriber.getId());
        long count = repo.countBySubscriber(managedSub);

        assertEquals(0, count);
    }
}
