package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.entity.TelegramSubscriberLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramSubscriberLocationRepository extends JpaRepository<TelegramSubscriberLocation, Long> {

    List<TelegramSubscriberLocation> findAllBySubscriber(TelegramSubscriber subscriber);

    long countBySubscriber(TelegramSubscriber subscriber);

    /** Erase a subscriber's saved locations (called when the subscriber itself is deleted). */
    void deleteBySubscriber(TelegramSubscriber subscriber);
}
