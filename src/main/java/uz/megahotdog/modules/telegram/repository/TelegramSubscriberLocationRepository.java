package uz.megahotdog.modules.telegram.repository;

import uz.megahotdog.modules.telegram.entity.TelegramSubscriber;
import uz.megahotdog.modules.telegram.entity.TelegramSubscriberLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramSubscriberLocationRepository extends JpaRepository<TelegramSubscriberLocation, Long> {

    List<TelegramSubscriberLocation> findAllBySubscriber(TelegramSubscriber subscriber);

    long countBySubscriber(TelegramSubscriber subscriber);
}
