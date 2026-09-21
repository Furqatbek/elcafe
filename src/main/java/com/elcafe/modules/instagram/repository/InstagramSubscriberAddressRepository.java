package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.entity.InstagramSubscriberAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstagramSubscriberAddressRepository extends JpaRepository<InstagramSubscriberAddress, Long> {

    List<InstagramSubscriberAddress> findAllBySubscriber(InstagramSubscriber subscriber);

    long countBySubscriber(InstagramSubscriber subscriber);
}
